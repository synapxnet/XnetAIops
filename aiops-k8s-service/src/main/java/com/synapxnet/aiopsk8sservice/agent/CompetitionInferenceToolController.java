package com.synapxnet.aiopsk8sservice.agent;

import com.synapxnet.goai.contract.AgentContract;
import com.synapxnet.goai.contract.AgentContractException;
import com.synapxnet.goai.contract.GovernedApprovalVerifier;
import com.synapxnet.goai.contract.GovernedResourceVersionTracker;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提供推荐推理容量场景的状态化 Live 工具，并保留逐步骤审批、幂等和资源版本证据。
 */
@RestController
public class CompetitionInferenceToolController {

    private final GovernedApprovalVerifier approvalVerifier;
    private final Map<String, InferenceState> incidentStates = new ConcurrentHashMap<>();
    private final GovernedResourceVersionTracker versionTracker = new GovernedResourceVersionTracker();

    /**
     * 创建比赛推理工具 Controller。
     *
     * @param approvalVerifier 通用计划级审批验证器
     */
    public CompetitionInferenceToolController(GovernedApprovalVerifier approvalVerifier) {
        this.approvalVerifier = approvalVerifier;
    }

    /**
     * 读取 GPU、队列、成功率和延迟指标。
     */
    @PostMapping("/api/agent/v1/tools/aiops.inference.metrics.get:invoke")
    public AgentContract.ToolResponse<Map<String, Object>> metrics(
            @RequestBody AgentContract.ToolRequest<InferenceMetricsArguments> body,
            HttpServletRequest servletRequest) {
        long startedNanos = System.nanoTime();
        AgentContract.RequestContext context = context(servletRequest, "aiops.inference.metrics.get", body);
        InferenceMetricsArguments arguments = requireMetricsArguments(body.arguments());
        InferenceState state = state(context.incidentId(), arguments.serviceUid());
        boolean recovered = state.recovered();
        Map<String, Object> data = Map.of(
                "serviceUid", arguments.serviceUid(),
                "deploymentUid", arguments.deploymentUid(),
                "gpuSmUtilization", recovered ? 0.72 : 1.0,
                "gpuMemoryBandwidthUtilization", recovered ? 0.68 : 1.0,
                "batchQueueSize", recovered ? 0 : 1000,
                "queueTimeoutRate", recovered ? 0.0005 : 0.15,
                "p99Ms", recovered ? 80 : 5000,
                "successRate", recovered ? 0.9995 : 0.95,
                "cpuUtilization", 0.40,
                "desiredReplicas", state.replicas());
        return AgentContract.success(data, context, "XnetAIops/inference-metrics", state.version(), startedNanos);
    }

    /**
     * 读取推理恢复计划的联合收敛状态。
     */
    @PostMapping("/api/agent/v1/tools/aiops.inference.recovery.status:invoke")
    public AgentContract.ToolResponse<Map<String, Object>> recoveryStatus(
            @RequestBody AgentContract.ToolRequest<RecoveryStatusArguments> body,
            HttpServletRequest servletRequest) {
        long startedNanos = System.nanoTime();
        AgentContract.RequestContext context = context(servletRequest, "aiops.inference.recovery.status", body);
        RecoveryStatusArguments arguments = requireRecoveryArguments(body.arguments());
        InferenceState state = state(context.incidentId(), arguments.serviceUid());
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
        return AgentContract.success(data, context, "XnetAIops/inference-recovery", state.version(), startedNanos);
    }

    /**
     * 保障预热 GPU 节点池容量。
     */
    @PostMapping("/api/agent/v1/tools/aiops.gpu.capacity.ensure:invoke")
    public AgentContract.ToolResponse<Map<String, Object>> ensureGpuCapacity(
            @RequestBody AgentContract.ToolRequest<GpuCapacityArguments> body,
            HttpServletRequest servletRequest) {
        AgentContract.RequestContext context = context(servletRequest, "aiops.gpu.capacity.ensure", body);
        GpuCapacityArguments arguments = requireGpuArguments(body.arguments());
        return executeWrite(body, context, () -> {
            InferenceState state = state(context.incidentId(), "recommendation");
            state.setGpuNodes(arguments.desiredGpuNodes());
            return Map.of("gpuNodes", state.gpuNodes(), "mode", arguments.mode());
        });
    }

    /**
     * 调整批大小、重复请求窗口和推理引擎配置。
     */
    @PostMapping("/api/agent/v1/tools/aiops.inference.runtime.tune:invoke")
    public AgentContract.ToolResponse<Map<String, Object>> tuneRuntime(
            @RequestBody AgentContract.ToolRequest<RuntimeTuneArguments> body,
            HttpServletRequest servletRequest) {
        AgentContract.RequestContext context = context(servletRequest, "aiops.inference.runtime.tune", body);
        RuntimeTuneArguments arguments = requireRuntimeArguments(body.arguments());
        return executeWrite(body, context, () -> {
            InferenceState state = state(context.incidentId(), "recommendation");
            state.setBatchSize(arguments.maxBatchSize());
            state.setRuntimeProfile(arguments.engineProfile());
            return Map.of(
                    "maxBatchSize", state.batchSize(),
                    "duplicateWindowMs", arguments.duplicateWindowMs(),
                    "engineProfile", state.runtimeProfile());
        });
    }

    /**
     * 应用推理 Deployment 副本数和批处理上限。
     */
    @PostMapping("/api/agent/v1/tools/aiops.inference.capacity.apply:invoke")
    public AgentContract.ToolResponse<Map<String, Object>> applyCapacity(
            @RequestBody AgentContract.ToolRequest<CapacityArguments> body,
            HttpServletRequest servletRequest) {
        AgentContract.RequestContext context = context(servletRequest, "aiops.inference.capacity.apply", body);
        CapacityArguments arguments = requireCapacityArguments(body.arguments());
        return executeWrite(body, context, () -> {
            InferenceState state = state(context.incidentId(), "recommendation");
            state.setReplicas(arguments.desiredReplicas());
            state.setBatchSize(arguments.maxBatchSize());
            return Map.of("replicas", state.replicas(), "maxBatchSize", state.batchSize());
        });
    }

    /**
     * 按固定阶梯将流量引入已就绪推理副本。
     */
    @PostMapping("/api/agent/v1/tools/aiops.inference.traffic.shift:invoke")
    public AgentContract.ToolResponse<Map<String, Object>> shiftTraffic(
            @RequestBody AgentContract.ToolRequest<TrafficShiftArguments> body,
            HttpServletRequest servletRequest) {
        AgentContract.RequestContext context = context(servletRequest, "aiops.inference.traffic.shift", body);
        TrafficShiftArguments arguments = requireTrafficArguments(body.arguments());
        return executeWrite(body, context, () -> {
            InferenceState state = state(context.incidentId(), arguments.serviceUid());
            state.setTrafficPercent(arguments.percentages().get(arguments.percentages().size() - 1));
            return Map.of("target", arguments.target(), "percentages", arguments.percentages(),
                    "trafficPercent", state.trafficPercent());
        });
    }

    /**
     * 更新基于队列和 P99 的弹性策略。
     */
    @PostMapping("/api/agent/v1/tools/aiops.inference.autoscaling.policy.update:invoke")
    public AgentContract.ToolResponse<Map<String, Object>> updateAutoscaling(
            @RequestBody AgentContract.ToolRequest<AutoscalingArguments> body,
            HttpServletRequest servletRequest) {
        AgentContract.RequestContext context = context(
                servletRequest, "aiops.inference.autoscaling.policy.update", body);
        AutoscalingArguments arguments = requireAutoscalingArguments(body.arguments());
        return executeWrite(body, context, () -> {
            InferenceState state = state(context.incidentId(), "recommendation");
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
     * 将临时推理容量收敛到稳定副本。
     */
    @PostMapping("/api/agent/v1/tools/aiops.inference.capacity.converge:invoke")
    public AgentContract.ToolResponse<Map<String, Object>> convergeCapacity(
            @RequestBody AgentContract.ToolRequest<CapacityConvergeArguments> body,
            HttpServletRequest servletRequest) {
        AgentContract.RequestContext context = context(
                servletRequest, "aiops.inference.capacity.converge", body);
        CapacityConvergeArguments arguments = requireConvergeArguments(body.arguments());
        return executeWrite(body, context, () -> {
            InferenceState state = state(context.incidentId(), "recommendation");
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
     * 执行单个计划级写步骤，并返回资源版本和平台审计回执。
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
        GovernedResourceVersionTracker.Execution execution = versionTracker.execute(
                context, body, mutation::apply);
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
     * 读取或创建事件级推理状态。
     */
    private InferenceState state(String incidentId, String serviceUid) {
        return incidentStates.computeIfAbsent(incidentId, ignored ->
                new InferenceState(serviceUid != null && serviceUid.contains("risk")));
    }

    /**
     * 从请求中读取已验证 Agent 上下文。
     */
    private AgentContract.RequestContext context(
            HttpServletRequest servletRequest,
            String toolName,
            AgentContract.ToolRequest<?> body) {
        return AgentContract.requireContext(servletRequest, toolName, body);
    }

    /** 校验推理指标参数。 */
    private InferenceMetricsArguments requireMetricsArguments(InferenceMetricsArguments value) {
        if (value == null) throw new AgentContractException(400, "INVALID_ARGUMENT", "arguments 不能为空");
        requireText(value.serviceUid(), "serviceUid");
        requireText(value.deploymentUid(), "deploymentUid");
        if (value.windowMinutes() != null && (value.windowMinutes() < 1 || value.windowMinutes() > 1440)) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "windowMinutes 超出范围");
        }
        return value;
    }

    /** 校验恢复状态参数。 */
    private RecoveryStatusArguments requireRecoveryArguments(RecoveryStatusArguments value) {
        if (value == null) throw new AgentContractException(400, "INVALID_ARGUMENT", "arguments 不能为空");
        requireText(value.serviceUid(), "serviceUid");
        requireText(value.deploymentUid(), "deploymentUid");
        return value;
    }

    /** 校验 GPU 容量参数。 */
    private GpuCapacityArguments requireGpuArguments(GpuCapacityArguments value) {
        if (value == null || value.desiredGpuNodes() == null || value.desiredGpuNodes() < 1
                || value.desiredGpuNodes() > 64 || !("ENSURE".equals(value.mode()) || "CONVERGE".equals(value.mode()))) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "GPU 容量参数无效");
        }
        requireText(value.clusterId(), "clusterId");
        requireText(value.nodePool(), "nodePool");
        return value;
    }

    /** 校验运行时调优参数。 */
    private RuntimeTuneArguments requireRuntimeArguments(RuntimeTuneArguments value) {
        if (value == null || value.maxBatchSize() == null || value.maxBatchSize() < 1
                || value.duplicateWindowMs() == null || value.duplicateWindowMs() < 0) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "推理运行时参数无效");
        }
        requireText(value.deploymentUid(), "deploymentUid");
        requireText(value.engineProfile(), "engineProfile");
        return value;
    }

    /** 校验容量扩展参数。 */
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

    /** 校验流量阶梯参数。 */
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

    /** 校验弹性策略参数。 */
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

    /** 校验容量收敛参数。 */
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

    /** 校验必填有界文本。 */
    private void requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 512 || value.indexOf('\0') >= 0) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", field + " 无效");
        }
    }

    /**
     * 判断推荐推理状态是否满足完整计划的最终稳态条件。
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

    /** 表示审批通过后的状态修改函数。 */
    @FunctionalInterface
    private interface Mutation {
        Map<String, Object> apply();
    }

    /** 保存事件级推荐推理沙盘状态。 */
    private static final class InferenceState {
        private int gpuNodes = 2;
        private int replicas = 6;
        private int batchSize = 64;
        private int trafficPercent;
        private String runtimeProfile = "BASELINE";
        private boolean autoscalingReady;
        private boolean converged;
        private final boolean initiallyStable;

        /** 创建推理状态；风控等非推荐场景保持稳定基线。 */
        private InferenceState(boolean initiallyStable) { this.initiallyStable = initiallyStable; }
        /** 判断业务是否达到恢复门槛。 */
        private boolean recovered() {
            return initiallyStable || hasRecoveredInferenceState(
                    gpuNodes, replicas, batchSize, trafficPercent,
                    autoscalingReady, converged, runtimeProfile);
        }
        /** 返回联合状态版本。 */
        private String version() { return recovered() ? "44" : "42"; }
        /** 返回 GPU 节点数。 */
        private int gpuNodes() { return gpuNodes; }
        /** 更新 GPU 节点数。 */
        private void setGpuNodes(int value) { gpuNodes = value; }
        /** 返回副本数。 */
        private int replicas() { return replicas; }
        /** 更新副本数。 */
        private void setReplicas(int value) { replicas = value; }
        /** 返回批大小。 */
        private int batchSize() { return batchSize; }
        /** 更新批大小。 */
        private void setBatchSize(int value) { batchSize = value; }
        /** 返回流量比例。 */
        private int trafficPercent() { return trafficPercent; }
        /** 更新流量比例。 */
        private void setTrafficPercent(int value) { trafficPercent = value; }
        /** 返回运行时配置。 */
        private String runtimeProfile() { return runtimeProfile; }
        /** 更新运行时配置。 */
        private void setRuntimeProfile(String value) { runtimeProfile = value; }
        /** 判断弹性策略是否就绪。 */
        private boolean autoscalingReady() { return autoscalingReady; }
        /** 更新弹性策略状态。 */
        private void setAutoscalingReady(boolean value) { autoscalingReady = value; }
        /** 更新容量收敛状态。 */
        private void setConverged(boolean value) { converged = value; }
    }

    /** 推理指标查询参数。 */
    public record InferenceMetricsArguments(String serviceUid, String deploymentUid, Integer windowMinutes) { }
    /** 恢复状态查询参数。 */
    public record RecoveryStatusArguments(String serviceUid, String deploymentUid) { }
    /** GPU 容量参数。 */
    public record GpuCapacityArguments(String clusterId, String nodePool, Integer desiredGpuNodes, String mode) { }
    /** 运行时调优参数。 */
    public record RuntimeTuneArguments(String deploymentUid, Integer maxBatchSize, Integer duplicateWindowMs, String engineProfile) { }
    /** 副本容量参数。 */
    public record CapacityArguments(String clusterId, String namespace, String name, Integer desiredReplicas, Integer maxBatchSize) { }
    /** 流量切换参数。 */
    public record TrafficShiftArguments(String serviceUid, String target, List<Integer> percentages) { }
    /** 弹性策略参数。 */
    public record AutoscalingArguments(String clusterId, String namespace, String name, Integer queueDepthTarget, Integer p99TargetMs, Integer minReplicas, Integer maxReplicas) { }
    /** 容量收敛参数。 */
    public record CapacityConvergeArguments(String clusterId, String namespace, String name, Integer stableReplicas, Integer maxBatchSize, Integer observationMinutes) { }
}
