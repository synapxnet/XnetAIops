/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-15
 * Version: 1.0.0 | Security Level: INTERNAL
 * __version__: 1.0.0 | __author__: maoyo | __copyright__: Copyright 2026 Synapxnet
 * __maintainer__: maoyo | __email__: synapxnet@gmail.com
 */
package com.synapxnet.aiopsk8sservice.agent;

import com.synapxnet.goai.contract.AgentContract;
import com.synapxnet.goai.contract.AgentContractException;
import com.synapxnet.goai.contract.GovernedApprovalVerifier;
import com.synapxnet.goai.contract.GovernedResourceVersionTracker;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.io.IOException;
import jakarta.annotation.PreDestroy;

/**
 * 提供推荐推理容量场景的状态化 Live 工具，并保留逐步骤审批、幂等和资源版本证据。 / Expose stateful inference tools with per-step approval, idempotency and version evidence.
 */
@org.springframework.context.annotation.Lazy(false)
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = {"openxnet.k8s.ui-reader", "openxnet.operations.read-only-service"},
        havingValue = "false", matchIfMissing = true)
@RestController
public class CompetitionInferenceToolController implements AutoCloseable {

    private static final String MIGRATION_SOURCE_JAR_SHA256 = "48325d55b67d06c9e9508b340c24a5839dc352f1d8c33c07a22707c3b48b7394";
    private static final String CURRENT_MIGRATION_SOURCE_JAR_SHA256 = "65d376a6f561d42360c5fdff002c8a6625c2fe560507bac6f8fe875b7b94e3c6";
    private static final String PERSISTENCE_BOOTSTRAP_SOURCE_JAR_SHA256 = "83784d96008415f2738924fe923bb44c2fb81b0b82fa798b844fb5c58ad9308c";

    private final GovernedApprovalVerifier approvalVerifier;
    private final Map<String, InferenceState> incidentStates = new ConcurrentHashMap<>();
    private GovernedResourceVersionTracker versionTracker = new GovernedResourceVersionTracker();
    private final Object stateLock = new Object();
    private GovernedStateCheckpointStore checkpointStore;

    /**
     * 创建比赛推理工具 Controller。 / Create the competition inference controller.
     *
     * @param approvalVerifier 通用计划级审批验证器
     */
    public CompetitionInferenceToolController(GovernedApprovalVerifier approvalVerifier) {
        this.approvalVerifier = approvalVerifier;
    }

    /** 启动时仅导入经过摘要确认且未消费的迁移快照；Import only a digest-verified, unconsumed migration snapshot at startup. */
    public CompetitionInferenceToolController(
            GovernedApprovalVerifier approvalVerifier,
            @Value("${goai.resource-state-migration-file:}") String migrationFile,
            @Value("${goai.resource-state-migration-sha256:}") String migrationDigest) {
        this(approvalVerifier);
        if (!migrationFile.isBlank()) restoreMigration(Path.of(migrationFile), migrationDigest);
        else if (!migrationDigest.isBlank()) throw new IllegalStateException("MIGRATION_FILE_REQUIRED");
    }

    /** 新持久化模式与一次性迁移互斥；先恢复完整状态再接受请求。 Makes persistence mutually exclusive with one-shot migration and restores complete state before serving requests. */
    @Autowired
    public CompetitionInferenceToolController(
            GovernedApprovalVerifier approvalVerifier,
            @Value("${goai.resource-state-migration-file:}") String migrationFile,
            @Value("${goai.resource-state-migration-sha256:}") String migrationDigest,
            @Value("${goai.resource-state-directory:}") String stateDirectory,
            @Value("${goai.resource-state-bootstrap-file:}") String bootstrapFile,
            @Value("${goai.resource-state-bootstrap-sha256:}") String bootstrapDigest) {
        this(approvalVerifier);
        if (stateDirectory.isBlank()) {
            if (!bootstrapFile.isBlank() || !bootstrapDigest.isBlank()) throw new IllegalStateException("CHECKPOINT_DIRECTORY_REQUIRED");
            if (!migrationFile.isBlank()) restoreMigration(Path.of(migrationFile), migrationDigest);
            else if (!migrationDigest.isBlank()) throw new IllegalStateException("MIGRATION_FILE_REQUIRED");
        } else {
            if (!migrationFile.isBlank() || !migrationDigest.isBlank()) throw new IllegalStateException("CHECKPOINT_LEGACY_MIGRATION_CONFLICT");
            initializeCheckpoint(Path.of(stateDirectory), bootstrapFile, bootstrapDigest, null);
        }
    }

    /** 离线测试只替换文件系统故障边界，保留真实控制器与版本逻辑。 Replaces only the filesystem fault boundary in offline tests while retaining actual controller and version logic. */
    CompetitionInferenceToolController(GovernedApprovalVerifier approvalVerifier, Path directory, Path bootstrap,
            String digest, GovernedStateCheckpointStore.Durability durability) {
        this(approvalVerifier);
        initializeCheckpoint(directory, bootstrap == null ? "" : bootstrap.toString(), digest, durability);
    }

    /** 加载当前 checkpoint，首次才读取精确绑定的 hg2 bootstrap。 Loads the current checkpoint and reads the exactly bound hg2 bootstrap only on first initialization. */
    private void initializeCheckpoint(Path directory, String bootstrapFile, String bootstrapDigest,
            GovernedStateCheckpointStore.Durability durability) {
        try {
            checkpointStore = durability == null ? new GovernedStateCheckpointStore(directory, bootstrapDigest)
                    : new GovernedStateCheckpointStore(directory, bootstrapDigest, durability);
            if (checkpointStore.initialized()) restoreCheckpointPayload(checkpointStore.load());
            else {
                restoreBootstrap(bootstrapFile, bootstrapDigest);
                checkpointStore.initialize(completeState());
            }
        } catch (Exception error) {
            if (checkpointStore != null) {
                try { checkpointStore.close(); } catch (IOException closeError) { error.addSuppressed(closeError); }
            }
            throw new IllegalStateException("GOVERNED_CHECKPOINT_STARTUP_FAILED", error);
        }
    }

    /** 严格验证新快照来源且不触碰任何旧 consumed 标记。 Strictly validates the new snapshot provenance without touching any existing consumed marker. */
    private void restoreBootstrap(String bootstrapFile, String expectedDigest) throws Exception {
        if (bootstrapFile.isBlank()) throw new IllegalStateException("CHECKPOINT_BOOTSTRAP_REQUIRED");
        Path source = Path.of(bootstrapFile);
        if (!source.isAbsolute() || !source.normalize().equals(source) || !Files.isRegularFile(source)
                || Files.isSymbolicLink(source) || !source.toRealPath().equals(source) || Files.size(source) > 4 * 1024 * 1024
                || Files.exists(source.resolveSibling(source.getFileName() + ".consumed"))) {
            throw new IllegalStateException("INVALID_OR_CONSUMED_BOOTSTRAP");
        }
        byte[] bytes = Files.readAllBytes(source);
        if (!expectedDigest.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))) {
            throw new IllegalStateException("CHECKPOINT_BOOTSTRAP_DIGEST_MISMATCH");
        }
        JsonNode document = strictMapper().readTree(bytes);
        if (!"openxnet.governed-state-export.v1".equals(document.path("schema").asText())
                || !"aiops".equals(document.path("platform").asText())
                || !getClass().getName().equals(document.path("controller").asText())
                || !PERSISTENCE_BOOTSTRAP_SOURCE_JAR_SHA256.equals(document.path("sourceJarSha256").asText())
                || !"incidentStates".equals(document.path("domainField").asText())
                || !document.path("readOnly").isBoolean() || !document.path("readOnly").booleanValue()
                || document.has("domainBindings")) {
            throw new IllegalStateException("CHECKPOINT_BOOTSTRAP_IDENTITY_MISMATCH");
        }
        Instant.parse(document.required("exportedAt").asText());
        var payload = strictMapper().createObjectNode();
        payload.set("tracker", document.required("tracker"));
        payload.set("domainState", document.required("domainState"));
        restoreCheckpointPayload(payload);
    }

    /** 拒绝重复字段、未知字段、缺失字段与浮点版本。 Rejects duplicate, unknown and missing fields and fractional versions. */
    private ObjectMapper strictMapper() {
        return new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }

    /** 同时复制版本、演练、幂等与全部领域字段。 Copies versions, rehearsals, idempotency records and all domain fields together. */
    private JsonNode completeState() {
        Map<String, InferenceSnapshot> domains = new java.util.TreeMap<>();
        incidentStates.forEach((key, state) -> domains.put(key, state.snapshot()));
        var mapper = strictMapper();
        var payload = mapper.createObjectNode();
        payload.set("tracker", mapper.valueToTree(versionTracker.snapshot()));
        payload.set("domainState", mapper.valueToTree(domains));
        return payload;
    }

    /** 先完整验证临时状态，再一起替换内存，禁止部分恢复。 Validates temporary state completely before replacing memory together, preventing partial restoration. */
    private void restoreCheckpointPayload(JsonNode payload) throws Exception {
        if (!payload.isObject() || payload.size() != 2 || !payload.path("domainState").isObject()) {
            throw new IllegalStateException("INVALID_CHECKPOINT_DOMAIN_PAYLOAD");
        }
        ObjectMapper mapper = strictMapper();
        var trackerSnapshot = mapper.treeToValue(payload.required("tracker"), GovernedResourceVersionTracker.StateSnapshot.class);
        var restoredTracker = new GovernedResourceVersionTracker();
        restoredTracker.restore(trackerSnapshot);
        Map<String, InferenceState> domains = decodeCanonicalDomains(mapper, payload.required("domainState"), trackerSnapshot);
        java.util.Set<String> boundVersions = new java.util.HashSet<>();
        for (String domainKey : domains.keySet()) {
            String[] key = domainKey.split(":", -1);
            for (String resource : InferenceResourceBinding.forService(key[1]).resourceIds()) boundVersions.add(key[0] + ":" + resource);
        }
        if (!boundVersions.equals(trackerSnapshot.liveVersions().keySet())) throw new IllegalStateException("CHECKPOINT_DOMAIN_COVERAGE_INCOMPLETE");
        versionTracker = restoredTracker;
        incidentStates.clear();
        incidentStates.putAll(domains);
    }

    /** 当前六类 supplier 只改内存；保存成功前不回成功，持久化失败后拒绝继续服务。 The six current suppliers mutate memory only; success follows persistence and persistence faults block further service. */
    private <T> T memoryTransaction(Supplier<T> operation) {
        synchronized (stateLock) {
            if (checkpointStore == null) return operation.get();
            JsonNode before = completeState();
            try {
                checkpointStore.verifyCurrent();
                T result = operation.get();
                checkpointStore.commit(completeState());
                return result;
            } catch (Exception error) {
                try { restoreCheckpointPayload(before); }
                catch (Exception rollbackError) { error.addSuppressed(rollbackError); }
                if (error instanceof AgentContractException domainError) throw domainError;
                throw new AgentContractException(503, "GOVERNED_CHECKPOINT_UNAVAILABLE", "治理状态暂不可用，需检查持久化并恢复服务。");
            }
        }
    }

    /** 应用退出时释放专用目录写锁。 Releases exclusive ownership of the state directory on application shutdown. */
    @PreDestroy
    @Override
    public void close() throws IOException {
        synchronized (stateLock) { if (checkpointStore != null) checkpointStore.close(); }
    }

    /** 保全原tracker和有证据绑定的领域状态，拒绝过期重复导入；Preserve the original tracker and evidenced domain bindings while rejecting stale replay imports. */
    private void restoreMigration(Path source, String expectedDigest) {
        try {
            if (!source.isAbsolute() || Files.isSymbolicLink(source) || !Files.isRegularFile(source)
                    || !expectedDigest.matches("[a-f0-9]{64}") || Files.size(source) > 4 * 1024 * 1024) {
                throw new IllegalStateException("INVALID_MIGRATION_ARTIFACT");
            }
            Path consumed = source.resolveSibling(source.getFileName() + ".consumed");
            if (Files.exists(consumed)) throw new IllegalStateException("MIGRATION_ALREADY_CONSUMED");
            byte[] bytes = Files.readAllBytes(source);
            if (!expectedDigest.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))) {
                throw new IllegalStateException("MIGRATION_DIGEST_MISMATCH");
            }
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            mapper.enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
            mapper.enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
            mapper.enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES);
            mapper.disable(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_FLOAT_AS_INT);
            JsonNode document = mapper.readTree(bytes);
            boolean canonicalDomains = CURRENT_MIGRATION_SOURCE_JAR_SHA256.equals(document.path("sourceJarSha256").asText());
            if (!"openxnet.governed-state-export.v1".equals(document.path("schema").asText())
                    || !"aiops".equals(document.path("platform").asText())
                    || !getClass().getName().equals(document.path("controller").asText())
                    || (!MIGRATION_SOURCE_JAR_SHA256.equals(document.path("sourceJarSha256").asText()) && !canonicalDomains)
                    || !"incidentStates".equals(document.path("domainField").asText())
                    || !document.path("readOnly").isBoolean() || !document.path("readOnly").booleanValue()) {
                throw new IllegalStateException("MIGRATION_IDENTITY_MISMATCH");
            }
            Instant.parse(document.required("exportedAt").asText());
            var trackerSnapshot = mapper.treeToValue(document.required("tracker"), GovernedResourceVersionTracker.StateSnapshot.class);
            JsonNode domains = document.required("domainState");
            if (!domains.isObject()) throw new IllegalStateException("INVALID_MIGRATION_DOMAIN_STATE");
            Map<String, InferenceState> restored;
            if (canonicalDomains) {
                if (document.has("domainBindings")) throw new IllegalStateException("UNEXPECTED_LEGACY_DOMAIN_BINDINGS");
                restored = decodeCanonicalDomains(mapper, domains, trackerSnapshot);
            } else {
                restored = decodeLegacyDomains(mapper, domains, document.required("domainBindings"), trackerSnapshot);
            }
            synchronized (versionTracker) {
                if (!incidentStates.isEmpty()) throw new IllegalStateException("MIGRATION_DOMAIN_TARGET_NOT_EMPTY");
                versionTracker.restore(trackerSnapshot);
                incidentStates.putAll(restored);
                Files.createFile(consumed);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("GOVERNED_STATE_MIGRATION_FAILED");
        }
    }

    /** 验证当前已规范领域键与完整版本，不要求读取初始化状态具备写入历史；Validate canonical domain keys and complete versions without requiring writes for read-initialized state. */
    private Map<String, InferenceState> decodeCanonicalDomains(ObjectMapper mapper, JsonNode domains,
            GovernedResourceVersionTracker.StateSnapshot trackerSnapshot) throws Exception {
        Map<String, InferenceState> restored = new java.util.LinkedHashMap<>();
        var entries = domains.fields();
        while (entries.hasNext()) {
            var entry = entries.next();
            String[] key = entry.getKey().split(":", -1);
            if (key.length != 2 || !key[0].matches("[A-Za-z0-9_-]{1,128}")) {
                throw new IllegalStateException("INVALID_CANONICAL_DOMAIN_KEY");
            }
            InferenceResourceBinding target = InferenceResourceBinding.forService(key[1]);
            InferenceSnapshot snapshot = mapper.treeToValue(entry.getValue(), InferenceSnapshot.class);
            if (snapshot.initiallyStable() != target.initiallyStable()) throw new IllegalStateException("MIGRATION_TARGET_MISMATCH");
            for (String resourceId : target.resourceIds()) {
                if (!trackerSnapshot.liveVersions().containsKey(key[0] + ":" + resourceId)) {
                    throw new IllegalStateException("MIGRATION_TARGET_VERSIONS_INCOMPLETE");
                }
            }
            restored.put(entry.getKey(), InferenceState.fromSnapshot(snapshot));
        }
        return restored;
    }

    /** 保留旧incident到目标的证据绑定与真实写入约束；Preserve legacy incident-to-target evidence bindings and retained-write requirements. */
    private Map<String, InferenceState> decodeLegacyDomains(ObjectMapper mapper, JsonNode domains, JsonNode bindings,
            GovernedResourceVersionTracker.StateSnapshot trackerSnapshot) throws Exception {
        if (!domains.isObject() || !bindings.isObject() || domains.size() != bindings.size()) {
            throw new IllegalStateException("MIGRATION_BINDINGS_INCOMPLETE");
        }
        Map<String, InferenceState> restored = new java.util.LinkedHashMap<>();
        var entries = domains.fields();
        while (entries.hasNext()) {
            var entry = entries.next();
            if (!entry.getKey().startsWith("inc_")) throw new IllegalStateException("INVALID_LEGACY_INCIDENT_KEY");
            JsonNode binding = bindings.required(entry.getKey());
            if (!binding.isObject() || binding.size() != 2) throw new IllegalStateException("INVALID_MIGRATION_BINDING");
            String workspaceId = binding.required("workspaceId").asText();
            String serviceUid = binding.required("serviceUid").asText();
            if (!workspaceId.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalStateException("INVALID_MIGRATION_WORKSPACE");
            InferenceResourceBinding target = InferenceResourceBinding.forService(serviceUid);
            InferenceSnapshot snapshot = mapper.treeToValue(entry.getValue(), InferenceSnapshot.class);
            if (snapshot.initiallyStable() != target.initiallyStable()) throw new IllegalStateException("MIGRATION_TARGET_MISMATCH");
            String prefix = workspaceId + ":" + entry.getKey() + ":";
            boolean retainedWrite = trackerSnapshot.executions().entrySet().stream().anyMatch(execution ->
                    execution.getKey().startsWith(prefix) && !execution.getValue().dryRun()
                            && target.resourceIds().contains(execution.getValue().resourceId()));
            if (!retainedWrite) throw new IllegalStateException("MIGRATION_BINDING_HAS_NO_RETAINED_WRITE");
            for (String resourceId : target.resourceIds()) {
                if (!trackerSnapshot.liveVersions().containsKey(workspaceId + ":" + resourceId)) {
                    throw new IllegalStateException("MIGRATION_TARGET_VERSIONS_INCOMPLETE");
                }
            }
            if (restored.put(workspaceId + ":" + serviceUid, InferenceState.fromSnapshot(snapshot)) != null) {
                throw new IllegalStateException("AMBIGUOUS_MIGRATION_TARGET");
            }
        }
        return restored;
    }

    /**
     * 读取 GPU、队列、成功率和延迟指标。 / Read GPU, queue, success-rate and latency metrics.
     */
    @PostMapping("/api/agent/v1/tools/aiops.inference.metrics.get:invoke")
    public AgentContract.ToolResponse<Map<String, Object>> metrics(
            @RequestBody AgentContract.ToolRequest<InferenceMetricsArguments> body,
            HttpServletRequest servletRequest) {
        long startedNanos = System.nanoTime();
        AgentContract.RequestContext context = context(servletRequest, "aiops.inference.metrics.get", body);
        InferenceMetricsArguments arguments = requireMetricsArguments(body.arguments());
        InferenceResourceBinding binding = InferenceResourceBinding.forRead(arguments.serviceUid(), arguments.deploymentUid());
        return memoryTransaction(() -> {
            InferenceState state = state(context.workspaceId(), binding.serviceUid());
            boolean recovered = state.recovered();
            Map<String, Object> data = new java.util.LinkedHashMap<>(Map.of(
                    "serviceUid", arguments.serviceUid(),
                    "deploymentUid", arguments.deploymentUid(),
                    "gpuSmUtilization", recovered ? 0.72 : 1.0,
                    "gpuMemoryBandwidthUtilization", recovered ? 0.68 : 1.0,
                    "batchQueueSize", recovered ? 0 : 1000,
                    "queueTimeoutRate", recovered ? 0.0005 : 0.15,
                    "p99Ms", recovered ? 80 : 5000,
                    "successRate", recovered ? 0.9995 : 0.95,
                    "cpuUtilization", 0.40,
                    "desiredReplicas", state.replicas()));
            data.put("resourceVersions", resourceVersions(context.workspaceId(), binding));
            return AgentContract.success(data, context, "XnetAIops/inference-metrics",
                    String.valueOf(versionTracker.currentVersion(context.workspaceId(), binding.runtimeId())), startedNanos);
        });
    }

    /**
     * 读取推理恢复计划的联合收敛状态。 / Read the combined convergence state of the inference recovery plan.
     */
    @PostMapping("/api/agent/v1/tools/aiops.inference.recovery.status:invoke")
    public AgentContract.ToolResponse<Map<String, Object>> recoveryStatus(
            @RequestBody AgentContract.ToolRequest<RecoveryStatusArguments> body,
            HttpServletRequest servletRequest) {
        long startedNanos = System.nanoTime();
        AgentContract.RequestContext context = context(servletRequest, "aiops.inference.recovery.status", body);
        RecoveryStatusArguments arguments = requireRecoveryArguments(body.arguments());
        InferenceResourceBinding binding = InferenceResourceBinding.forRead(arguments.serviceUid(), arguments.deploymentUid());
        return memoryTransaction(() -> {
            InferenceState state = state(context.workspaceId(), binding.serviceUid());
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("serviceUid", arguments.serviceUid());
            data.put("deploymentUid", arguments.deploymentUid());
            data.put("status", state.recovered() ? "STABLE" : "DEGRADED");
            data.put("runtimeProfile", state.runtimeProfile());
            data.put("gpuNodes", state.gpuNodes());
            data.put("replicas", state.replicas());
            data.put("trafficPercent", state.trafficPercent());
            data.put("autoscalingPolicyReady", state.autoscalingReady());
            data.put("queueDepth", state.recovered() ? 0 : 1000);
            data.put("p99Ms", state.recovered() ? 80 : 5000);
            data.put("successRate", state.recovered() ? 0.9995 : 0.95);
            data.put("recovered", state.recovered());
            data.put("queueAwareAutoscaling", state.autoscalingReady() || state.recovered());
            data.put("businessKpiRecovered", state.recovered());
            data.put("passed", state.recovered());
            data.put("resourceVersions", resourceVersions(context.workspaceId(), binding));
            return AgentContract.success(data, context, "XnetAIops/inference-recovery",
                    String.valueOf(versionTracker.currentVersion(context.workspaceId(), binding.runtimeId())), startedNanos);
        });
    }

    /**
     * 保障预热 GPU 节点池容量。 / Ensure capacity in the prewarmed GPU node pool.
     */
    @PostMapping("/api/agent/v1/tools/aiops.gpu.capacity.ensure:invoke")
    public AgentContract.ToolResponse<Map<String, Object>> ensureGpuCapacity(
            @RequestBody AgentContract.ToolRequest<GpuCapacityArguments> body,
            HttpServletRequest servletRequest) {
        AgentContract.RequestContext context = context(servletRequest, "aiops.gpu.capacity.ensure", body);
        GpuCapacityArguments arguments = requireGpuArguments(body.arguments());
        return executeWrite(body, context, () -> {
            InferenceState state = state(context.workspaceId(), "service_rec_inference");
            state.setGpuNodes(arguments.desiredGpuNodes());
            return Map.of("gpuNodes", state.gpuNodes(), "mode", arguments.mode());
        });
    }

    /**
     * 调整批大小、重复请求窗口和推理引擎配置。 / Tune batch size, duplicate-request window and inference profile.
     */
    @PostMapping("/api/agent/v1/tools/aiops.inference.runtime.tune:invoke")
    public AgentContract.ToolResponse<Map<String, Object>> tuneRuntime(
            @RequestBody AgentContract.ToolRequest<RuntimeTuneArguments> body,
            HttpServletRequest servletRequest) {
        AgentContract.RequestContext context = context(servletRequest, "aiops.inference.runtime.tune", body);
        RuntimeTuneArguments arguments = requireRuntimeArguments(body.arguments());
        return executeWrite(body, context, () -> {
            InferenceState state = state(context.workspaceId(), "service_rec_inference");
            state.setBatchSize(arguments.maxBatchSize());
            state.setRuntimeProfile(arguments.engineProfile());
            return Map.of(
                    "maxBatchSize", state.batchSize(),
                    "duplicateWindowMs", arguments.duplicateWindowMs(),
                    "engineProfile", state.runtimeProfile());
        });
    }

    /**
     * 应用推理 Deployment 副本数和批处理上限。 / Apply inference replica count and batch limits.
     */
    @PostMapping("/api/agent/v1/tools/aiops.inference.capacity.apply:invoke")
    public AgentContract.ToolResponse<Map<String, Object>> applyCapacity(
            @RequestBody AgentContract.ToolRequest<CapacityArguments> body,
            HttpServletRequest servletRequest) {
        AgentContract.RequestContext context = context(servletRequest, "aiops.inference.capacity.apply", body);
        CapacityArguments arguments = requireCapacityArguments(body.arguments());
        return executeWrite(body, context, () -> {
            InferenceState state = state(context.workspaceId(), "service_rec_inference");
            state.setReplicas(arguments.desiredReplicas());
            state.setBatchSize(arguments.maxBatchSize());
            return Map.of("replicas", state.replicas(), "maxBatchSize", state.batchSize());
        });
    }

    /**
     * 按固定阶梯将流量引入已就绪推理副本。 / Shift traffic to ready inference replicas in fixed stages.
     */
    @PostMapping("/api/agent/v1/tools/aiops.inference.traffic.shift:invoke")
    public AgentContract.ToolResponse<Map<String, Object>> shiftTraffic(
            @RequestBody AgentContract.ToolRequest<TrafficShiftArguments> body,
            HttpServletRequest servletRequest) {
        AgentContract.RequestContext context = context(servletRequest, "aiops.inference.traffic.shift", body);
        TrafficShiftArguments arguments = requireTrafficArguments(body.arguments());
        return executeWrite(body, context, () -> {
            InferenceState state = state(context.workspaceId(), arguments.serviceUid());
            state.setTrafficPercent(arguments.percentages().get(arguments.percentages().size() - 1));
            return Map.of("target", arguments.target(), "percentages", arguments.percentages(),
                    "trafficPercent", state.trafficPercent());
        });
    }

    /**
     * 更新基于队列和 P99 的弹性策略。 / Update queue and P99 autoscaling policy.
     */
    @PostMapping("/api/agent/v1/tools/aiops.inference.autoscaling.policy.update:invoke")
    public AgentContract.ToolResponse<Map<String, Object>> updateAutoscaling(
            @RequestBody AgentContract.ToolRequest<AutoscalingArguments> body,
            HttpServletRequest servletRequest) {
        AgentContract.RequestContext context = context(
                servletRequest, "aiops.inference.autoscaling.policy.update", body);
        AutoscalingArguments arguments = requireAutoscalingArguments(body.arguments());
        return executeWrite(body, context, () -> {
            InferenceState state = state(context.workspaceId(), "service_rec_inference");
            state.setAutoscalingReady(true);
            return Map.of(
                    "queueDepthTarget", arguments.queueDepthTarget(),
                    "p99TargetMs", arguments.p99TargetMs(),
                    "minReplicas", arguments.minReplicas(),
                    "maxReplicas", arguments.maxReplicas(),
                    "status", "APPLIED");
        });
    }

    /**
     * 将临时推理容量收敛到稳定副本。 / Converge temporary inference capacity to stable replicas.
     */
    @PostMapping("/api/agent/v1/tools/aiops.inference.capacity.converge:invoke")
    public AgentContract.ToolResponse<Map<String, Object>> convergeCapacity(
            @RequestBody AgentContract.ToolRequest<CapacityConvergeArguments> body,
            HttpServletRequest servletRequest) {
        AgentContract.RequestContext context = context(
                servletRequest, "aiops.inference.capacity.converge", body);
        CapacityConvergeArguments arguments = requireConvergeArguments(body.arguments());
        return executeWrite(body, context, () -> {
            InferenceState state = state(context.workspaceId(), "service_rec_inference");
            state.setReplicas(arguments.stableReplicas());
            state.setBatchSize(arguments.maxBatchSize());
            state.setConverged(true);
            return Map.of(
                    "stableReplicas", state.replicas(),
                    "maxBatchSize", state.batchSize(),
                    "observationMinutes", arguments.observationMinutes(),
                    "status", "SUCCEEDED");
        });
    }

    /**
     * 执行单个计划级写步骤，并返回资源版本和平台审计回执。 / Execute one governed write and return versioned audit receipts.
     *
     * @param body 工具请求
     * @param context 已鉴权上下文
     * @param mutation 通过审批后的状态变更
     * @return 统一写工具响应
     */
    private AgentContract.ToolResponse<Map<String, Object>> executeWrite(
            AgentContract.ToolRequest<?> body,
            AgentContract.RequestContext context,
            Mutation mutation) {
        long startedNanos = System.nanoTime();
        GovernedApprovalVerifier.ApprovalDecision decision = approvalVerifier.verify(body, context);
        requireCanonicalWriteTarget(body);
        GovernedResourceVersionTracker.Execution execution = memoryTransaction(() -> {
            state(context.workspaceId(), "service_rec_inference");
            return versionTracker.execute(context, body, mutation::apply);
        });
        Map<String, Object> domain = execution.data();
        long beforeVersion = execution.beforeVersion();
        long afterVersion = execution.afterVersion();
        String idempotencyKey = context.workspaceId() + ":" + context.incidentId()
                + ":" + context.idempotencyKey();
        String actionId = "aiops-" + UUID.nameUUIDFromBytes(idempotencyKey.getBytes()).toString();
        Map<String, Object> data = new java.util.LinkedHashMap<>(domain);
        data.put("actionId", actionId);
        data.put("status", Boolean.TRUE.equals(body.dryRun()) ? "DRY_RUN" : "SUCCEEDED");
        data.put("stepId", body.stepId());
        data.put("planDigest", body.planDigest());
        AgentContract.AuditReceipt receipt = new AgentContract.AuditReceipt(
                "receipt-" + actionId, context.requestId(), context.workspaceId(), context.incidentId(),
                context.traceId(), context.toolName(), context.actorId(), decision.approverId(),
                body.approvalId(), decision.argumentsDigest(), actionId, String.valueOf(data.get("status")),
                String.valueOf(beforeVersion), String.valueOf(afterVersion), Instant.now(), Instant.now(), List.of());
        return AgentContract.successWithReceipt(
                Map.copyOf(data), context, "XnetAIops/inference-control", String.valueOf(afterVersion),
                startedNanos, receipt);
    }

    /**
     * 按Workspace和已登记服务读取持续状态；Read target state across incidents within a workspace.
     */
    private InferenceState state(String workspaceId, String serviceUid) {
        InferenceResourceBinding binding = InferenceResourceBinding.forService(serviceUid);
        for (String resourceId : binding.resourceIds()) versionTracker.initializeResource(workspaceId, resourceId, 42L);
        return incidentStates.computeIfAbsent(workspaceId + ":" + serviceUid,
                ignored -> new InferenceState(binding.initiallyStable()));
    }

    /** 从同一个写tracker读取相关资源版本；Read associated versions from the same write tracker. */
    private Map<String, String> resourceVersions(String workspaceId, InferenceResourceBinding binding) {
        Map<String, String> versions = new java.util.LinkedHashMap<>();
        for (String resourceId : binding.resourceIds()) {
            versions.put(resourceId, String.valueOf(versionTracker.currentVersion(workspaceId, resourceId)));
        }
        return Map.copyOf(versions);
    }

    /** 审批资源必须与工具参数及登记目标一致；Require the approved resource to match arguments and the registered target. */
    private void requireCanonicalWriteTarget(AgentContract.ToolRequest<?> body) {
        Object arguments = body.arguments();
        String canonical;
        if (arguments instanceof GpuCapacityArguments value) canonical = value.clusterId() + "/" + value.nodePool();
        else if (arguments instanceof RuntimeTuneArguments value) canonical = value.deploymentUid() + "/runtime";
        else if (arguments instanceof CapacityArguments value) canonical = value.clusterId() + "/" + value.namespace() + "/" + value.name();
        else if (arguments instanceof CapacityConvergeArguments value) canonical = value.clusterId() + "/" + value.namespace() + "/" + value.name();
        else if (arguments instanceof AutoscalingArguments value) canonical = value.clusterId() + "/" + value.namespace() + "/" + value.name() + "/autoscaling";
        else if (arguments instanceof TrafficShiftArguments value) canonical = value.serviceUid() + "/traffic";
        else throw new AgentContractException(400, "INVALID_ARGUMENT", "Unsupported inference write arguments.");
        if (!canonical.equals(body.resourceId()) || !InferenceResourceBinding.recommendation().resourceIds().contains(canonical)) {
            throw new AgentContractException(409, "RESOURCE_SCOPE_MISMATCH", "Approved resource does not match the registered inference target.");
        }
    }

    /**
     * 从请求中读取已验证 Agent 上下文。 / Read the authenticated Agent request context.
     */
    private AgentContract.RequestContext context(
            HttpServletRequest servletRequest,
            String toolName,
            AgentContract.ToolRequest<?> body) {
        return AgentContract.requireContext(servletRequest, toolName, body);
    }

    /** 校验推理指标参数。 / Validate inference metric arguments. */
    private InferenceMetricsArguments requireMetricsArguments(InferenceMetricsArguments value) {
        if (value == null) throw new AgentContractException(400, "INVALID_ARGUMENT", "arguments 不能为空");
        requireText(value.serviceUid(), "serviceUid");
        requireText(value.deploymentUid(), "deploymentUid");
        if (value.windowMinutes() != null && (value.windowMinutes() < 1 || value.windowMinutes() > 1440)) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "windowMinutes 超出范围");
        }
        return value;
    }

    /** 校验恢复状态参数。 / Validate recovery-status arguments. */
    private RecoveryStatusArguments requireRecoveryArguments(RecoveryStatusArguments value) {
        if (value == null) throw new AgentContractException(400, "INVALID_ARGUMENT", "arguments 不能为空");
        requireText(value.serviceUid(), "serviceUid");
        requireText(value.deploymentUid(), "deploymentUid");
        return value;
    }

    /** 校验 GPU 容量参数。 / Describe GPU-capacity arguments. / Validate GPU-capacity arguments. */
    private GpuCapacityArguments requireGpuArguments(GpuCapacityArguments value) {
        if (value == null || value.desiredGpuNodes() == null || value.desiredGpuNodes() < 1
                || value.desiredGpuNodes() > 64 || !("ENSURE".equals(value.mode()) || "CONVERGE".equals(value.mode()))) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "GPU 容量参数无效");
        }
        requireText(value.clusterId(), "clusterId");
        requireText(value.nodePool(), "nodePool");
        return value;
    }

    /** 校验运行时调优参数。 / Describe runtime-tuning arguments. / Validate runtime-tuning arguments. */
    private RuntimeTuneArguments requireRuntimeArguments(RuntimeTuneArguments value) {
        if (value == null || value.maxBatchSize() == null || value.maxBatchSize() < 1
                || value.duplicateWindowMs() == null || value.duplicateWindowMs() < 0) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "推理运行时参数无效");
        }
        requireText(value.deploymentUid(), "deploymentUid");
        requireText(value.engineProfile(), "engineProfile");
        return value;
    }

    /** 校验容量扩展参数。 / Validate capacity-expansion arguments. */
    private CapacityArguments requireCapacityArguments(CapacityArguments value) {
        if (value == null || value.desiredReplicas() == null || value.desiredReplicas() < 1
                || value.maxBatchSize() == null || value.maxBatchSize() < 1) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "推理容量参数无效");
        }
        requireText(value.clusterId(), "clusterId");
        requireText(value.namespace(), "namespace");
        requireText(value.name(), "name");
        return value;
    }

    /** 校验流量阶梯参数。 / Validate progressive-traffic arguments. */
    private TrafficShiftArguments requireTrafficArguments(TrafficShiftArguments value) {
        if (value == null || value.percentages() == null || value.percentages().isEmpty()
                || value.percentages().size() > 8
                || value.percentages().stream().anyMatch(item -> item == null || item < 0 || item > 100)) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "流量阶梯参数无效");
        }
        requireText(value.serviceUid(), "serviceUid");
        requireText(value.target(), "target");
        return value;
    }

    /** 校验弹性策略参数。 / Describe autoscaling-policy arguments. / Validate autoscaling-policy arguments. */
    private AutoscalingArguments requireAutoscalingArguments(AutoscalingArguments value) {
        if (value == null || value.queueDepthTarget() == null || value.queueDepthTarget() < 0
                || value.p99TargetMs() == null || value.p99TargetMs() < 1
                || value.minReplicas() == null || value.maxReplicas() == null
                || value.minReplicas() < 1 || value.maxReplicas() < value.minReplicas()) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "弹性策略参数无效");
        }
        requireText(value.clusterId(), "clusterId");
        requireText(value.namespace(), "namespace");
        requireText(value.name(), "name");
        return value;
    }

    /** 校验容量收敛参数。 / Describe capacity-convergence arguments. / Validate capacity-convergence arguments. */
    private CapacityConvergeArguments requireConvergeArguments(CapacityConvergeArguments value) {
        if (value == null || value.stableReplicas() == null || value.stableReplicas() < 1
                || value.maxBatchSize() == null || value.maxBatchSize() < 1
                || value.observationMinutes() == null || value.observationMinutes() < 1) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "容量收敛参数无效");
        }
        requireText(value.clusterId(), "clusterId");
        requireText(value.namespace(), "namespace");
        requireText(value.name(), "name");
        return value;
    }

    /** 校验必填有界文本。 / Validate required bounded text. */
    private void requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 512 || value.indexOf('\0') >= 0) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", field + " 无效");
        }
    }

    /**
     * 判断推荐推理状态是否满足完整计划的最终稳态条件。 / Determine whether inference state meets the complete plan stability conditions.
     *
     * @param gpuNodes GPU 节点数
     * @param replicas 稳定副本数
     * @param batchSize 稳态批大小
     * @param trafficPercent 已切换流量比例
     * @param autoscalingReady 队列弹性策略是否就绪
     * @param converged 临时容量是否已收敛
     * @param runtimeProfile 最终推理引擎配置
     * @return 是否达到独立验证门槛
     */
    static boolean hasRecoveredInferenceState(
            int gpuNodes,
            int replicas,
            int batchSize,
            int trafficPercent,
            boolean autoscalingReady,
            boolean converged,
            String runtimeProfile) {
        return gpuNodes >= 4
                && replicas >= 12
                && batchSize == 64
                && trafficPercent == 100
                && autoscalingReady
                && converged
                && "DYNAMIC_SHAPE_OPTIMIZED".equals(runtimeProfile);
    }

    /** 表示审批通过后的状态修改函数。 / Represent a state mutation after approval. */
    @FunctionalInterface
    private interface Mutation {
        /** 执行已审批领域修改；Apply the approved domain mutation. */
        Map<String, Object> apply();
    }

    /** 保存事件级推荐推理沙盘状态。 / Retain registered inference target state across incidents. */
    private static final class InferenceState {
        private int gpuNodes = 2;
        private int replicas = 6;
        private int batchSize = 64;
        private int trafficPercent;
        private String runtimeProfile = "BASELINE";
        private boolean autoscalingReady;
        private boolean converged;
        private final boolean initiallyStable;

        /** 创建推理状态；风控等非推荐场景保持稳定基线。 / Create inference state with the registered risk baseline. */
        private InferenceState(boolean initiallyStable) { this.initiallyStable = initiallyStable; }

        /** 从完整领域快照恢复原值，拒绝缺失或越界状态；Restore exact domain values and reject incomplete or out-of-range state. */
        private static InferenceState fromSnapshot(InferenceSnapshot value) {
            if (value.gpuNodes() < 1 || value.gpuNodes() > 64 || value.replicas() < 1
                    || value.batchSize() < 1 || value.trafficPercent() < 0 || value.trafficPercent() > 100
                    || value.runtimeProfile() == null || value.runtimeProfile().isBlank()) {
                throw new IllegalStateException("INVALID_MIGRATION_DOMAIN_STATE");
            }
            InferenceState restored = new InferenceState(value.initiallyStable());
            restored.gpuNodes = value.gpuNodes();
            restored.replicas = value.replicas();
            restored.batchSize = value.batchSize();
            restored.trafficPercent = value.trafficPercent();
            restored.runtimeProfile = value.runtimeProfile();
            restored.autoscalingReady = value.autoscalingReady();
            restored.converged = value.converged();
            return restored;
        }
        /** 导出所有领域字段，不遗漏恢复判定所需信息。 Exports every domain field needed for recovery decisions. */
        private InferenceSnapshot snapshot() {
            return new InferenceSnapshot(gpuNodes, replicas, batchSize, trafficPercent,
                    runtimeProfile, autoscalingReady, converged, initiallyStable);
        }
        /** 判断业务是否达到恢复门槛。 / Determine whether business recovery thresholds are met. */
        private boolean recovered() {
            return initiallyStable || hasRecoveredInferenceState(
                    gpuNodes, replicas, batchSize, trafficPercent,
                    autoscalingReady, converged, runtimeProfile);
        }
        /** 返回 GPU 节点数。 / Return the GPU node count. */
        private int gpuNodes() { return gpuNodes; }
        /** 更新 GPU 节点数。 / Update the GPU node count. */
        private void setGpuNodes(int value) { gpuNodes = value; }
        /** 返回副本数。 / Return the replica count. */
        private int replicas() { return replicas; }
        /** 更新副本数。 / Update the replica count. */
        private void setReplicas(int value) { replicas = value; }
        /** 返回批大小。 / Return the batch size. */
        private int batchSize() { return batchSize; }
        /** 更新批大小。 / Update the batch size. */
        private void setBatchSize(int value) { batchSize = value; }
        /** 返回流量比例。 / Return the traffic percentage. */
        private int trafficPercent() { return trafficPercent; }
        /** 更新流量比例。 / Update the traffic percentage. */
        private void setTrafficPercent(int value) { trafficPercent = value; }
        /** 返回运行时配置。 / Return the runtime profile. */
        private String runtimeProfile() { return runtimeProfile; }
        /** 更新运行时配置。 / Update the runtime profile. */
        private void setRuntimeProfile(String value) { runtimeProfile = value; }
        /** 判断弹性策略是否就绪。 / Determine whether autoscaling is ready. */
        private boolean autoscalingReady() { return autoscalingReady; }
        /** 更新弹性策略状态。 / Update autoscaling readiness. */
        private void setAutoscalingReady(boolean value) { autoscalingReady = value; }
        /** 更新容量收敛状态。 / Update capacity convergence. */
        private void setConverged(boolean value) { converged = value; }
    }

    /** 推理指标查询参数。 / Describe inference metric query arguments. */
    public record InferenceMetricsArguments(String serviceUid, String deploymentUid, Integer windowMinutes) { }

    /** 原内存领域状态的完整迁移结构；Represent the complete original in-memory domain state for migration. */
    public record InferenceSnapshot(int gpuNodes, int replicas, int batchSize, int trafficPercent,
                                    String runtimeProfile, boolean autoscalingReady, boolean converged, boolean initiallyStable) { }
    /** 恢复状态查询参数。 / Describe recovery-status query arguments. */
    public record RecoveryStatusArguments(String serviceUid, String deploymentUid) { }
    /** GPU 容量参数。 / Describe GPU-capacity arguments. */
    public record GpuCapacityArguments(String clusterId, String nodePool, Integer desiredGpuNodes, String mode) { }
    /** 运行时调优参数。 / Describe runtime-tuning arguments. */
    public record RuntimeTuneArguments(String deploymentUid, Integer maxBatchSize, Integer duplicateWindowMs, String engineProfile) { }
    /** 副本容量参数。 / Describe replica-capacity arguments. */
    public record CapacityArguments(String clusterId, String namespace, String name, Integer desiredReplicas, Integer maxBatchSize) { }
    /** 流量切换参数。 / Describe traffic-shift arguments. */
    public record TrafficShiftArguments(String serviceUid, String target, List<Integer> percentages) { }
    /** 弹性策略参数。 / Describe autoscaling-policy arguments. */
    public record AutoscalingArguments(String clusterId, String namespace, String name, Integer queueDepthTarget, Integer p99TargetMs, Integer minReplicas, Integer maxReplicas) { }
    /** 容量收敛参数。 / Describe capacity-convergence arguments. */
    public record CapacityConvergeArguments(String clusterId, String namespace, String name, Integer stableReplicas, Integer maxBatchSize, Integer observationMinutes) { }
}
