/* Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 隔离真实特征漂移执行客户端 / Isolated real feature-drift execution client.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-18
 * Version: 1.3.0 | Security Level: INTERNAL
 * __version__: 1.3.0 | __author__: maoyo | __copyright__: Copyright 2026 Synapxnet
 * __maintainer__: maoyo | __email__: synapxnet@gmail.com
 */
package com.synapxnet.goai.contract;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 仅代理登记的风险演练目标，不允许失败后回退旧状态。 / Proxy registered risk targets without fallback after failure. */
public final class FeatureDriftRuntimeClient {
    private static final String SOURCE_MODE = "REAL_CPU_SYNTHETIC_STAGING";
    private static final Map<String, String> ACTIONS = Map.ofEntries(
        Map.entry("dataops.feature.backfill.start", "backfill"),
        Map.entry("mlops.feature.pipeline.publish", "feature-publish"),
        Map.entry("mlops.training.search.start", "train"),
        Map.entry("mlops.model.iteration.start", "train"),
        Map.entry("mlops.model.evaluation.run", "evaluate"),
        Map.entry("mlops.model.register", "register"),
        Map.entry("mlops.feature.fallback.apply", "fallback"),
        Map.entry("mlops.feature.fallback.remove", "fallback-remove"),
        Map.entry("mlops.deployment.canary.apply", "canary"),
        Map.entry("mlops.deployment.promote", "promote"),
        Map.entry("mlops.deployment.rollback", "rollback"));
    private static final Set<String> READS = Set.of("aiops.alert.get", "aiops.service.health",
        "aiops.inference.metrics.get", "aiops.inference.recovery.status", "dataops.quality.report.get",
        "dataops.schema.snapshot.get", "dataops.lineage.get", "dataops.workflow.instance.get",
        "dataops.dataset.validation.get", "mlops.deployment.get", "mlops.inference.probe",
        "mlops.release.validation.get", "mlops.attribution.report.get");
    private final boolean enabled;
    private final URI base;
    private final String token;
    private final String platform;
    private final ObjectMapper mapper;
    private final GovernedApprovalVerifier verifier;
    private final HttpClient http;

    /** 从固定服务配置与角色密钥文件建立客户端。 / Create a client using fixed configuration and a role credential file. */
    public FeatureDriftRuntimeClient(boolean enabled, String url, String tokenFile, String platform,
            ObjectMapper mapper, GovernedApprovalVerifier verifier) {
        this(enabled, url, readToken(enabled, tokenFile), platform, mapper, verifier,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
    }

    /** 测试可注入传输，生产仍使用只读密钥文件。 / Inject transport for tests while production uses a credential file. */
    FeatureDriftRuntimeClient(boolean enabled, String url, String token, String platform,
            ObjectMapper mapper, GovernedApprovalVerifier verifier, HttpClient http) {
        this.enabled = enabled; this.mapper = mapper; this.verifier = verifier; this.http = http;
        this.platform = platform; this.token = token;
        if (!Set.of("aiops", "dataops", "mlops").contains(platform)) throw new IllegalArgumentException("Invalid runtime platform");
        if (enabled) {
            URI candidate = URI.create(url);
            if (!Set.of("http", "https").contains(candidate.getScheme()) || candidate.getHost() == null
                    || candidate.getRawUserInfo() != null || candidate.getRawQuery() != null || candidate.getRawFragment() != null
                    || !(candidate.getPath().isEmpty() || candidate.getPath().equals("/")) || token.length() < 32) {
                throw new IllegalArgumentException("Invalid fixed feature-drift runtime configuration");
            }
            this.base = candidate;
        } else this.base = null;
    }

    /** 启用时必须读取现有普通令牌文件，不回显路径或内容。 / Require an existing regular token file when enabled, without echoing it. */
    private static String readToken(boolean enabled, String file) {
        if (!enabled) return "";
        try {
            Path path = Path.of(file);
            if (!path.isAbsolute() || !Files.isRegularFile(path) || Files.size(path) > 8192) throw new IllegalArgumentException();
            String value = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (value.length() < 32 || value.contains("\n") || value.contains("\r")) throw new IllegalArgumentException();
            return value;
        } catch (Exception error) { throw new IllegalStateException("Feature-drift role credential unavailable"); }
    }

    /** 仅匹配本平台固定工具及完整风险资源身份。 / Match this platform's fixed tools and exact risk resource identities. */
    public boolean handles(String tool, Object arguments) {
        if (!enabled || tool == null || !tool.startsWith(platform + ".") || (!READS.contains(tool) && !ACTIONS.containsKey(tool))) return false;
        Map<String, Object> args = arguments(arguments);
        if (tool.startsWith("mlops.")) return "deploy_risk_prod".equals(args.get("deploymentUid"));
        if (tool.startsWith("aiops.")) return "alert_risk_error_rate".equals(args.get("alertUid"))
            || "alert_risk_error_rate".equals(args.get("alertId")) || "service_risk_inference".equals(args.get("serviceUid"));
        return "asset_risk_features_prod".equals(args.get("assetUid")) || "qr_risk_features_120".equals(args.get("reportUid"))
            || "task_risk_features_latest".equals(args.get("instanceUid"))
            || "dataset_risk_repaired_19".equals(args.get("datasetUid"));
    }

    /** 代理已鉴权工具，并将运行时真实回执封装为原有公共契约。 / Proxy an authenticated tool and wrap actual runtime receipts in the existing contract. */
    @SuppressWarnings("unchecked")
    public <T> AgentContract.ToolResponse<T> invoke(AgentContract.ToolRequest<?> body, AgentContract.RequestContext context) {
        long started = System.nanoTime();
        if (!handles(context.toolName(), body.arguments())) throw error(403, "FEATURE_DRIFT_TARGET_FORBIDDEN");
        requireId(context.workspaceId()); requireId(context.incidentId()); requireId(context.traceId());
        boolean mutation = ACTIONS.containsKey(context.toolName()) && !context.toolName().equals("mlops.model.evaluation.run");
        GovernedApprovalVerifier.ApprovalDecision approval = mutation ? verifier.verify(body, context) : null;
        Map<String, Object> envelope = request(body, context, approval);
        Map<String, Object> result = exchange(body, context, envelope);
        Object raw = result.get("data");
        if (!(raw instanceof Map<?, ?>)) throw error(502, "FEATURE_DRIFT_RESPONSE_INVALID");
        Map<String, Object> data = (Map<String, Object>) raw;
        if (!SOURCE_MODE.equals(data.get("sourceMode")) || !context.workspaceId().equals(data.get("workspaceId"))
                || !context.incidentId().equals(data.get("incidentId")) || !context.traceId().equals(data.get("traceId"))) {
            throw error(502, "FEATURE_DRIFT_SCOPE_MISMATCH");
        }
        String version = string(result.get("resourceVersion"));
        if (version.isBlank()) throw error(502, "FEATURE_DRIFT_VERSION_MISSING");
        String source = "Xnet" + platform.substring(0, 1).toUpperCase() + platform.substring(1) + "/feature-drift-runtime";
        if (!mutation) return AgentContract.success((T) data, context, source, version, started);
        if (!(result.get("actionReceipt") instanceof Map<?, ?> receipt)) throw error(502, "FEATURE_DRIFT_RECEIPT_MISSING");
        String expectedStatus = Boolean.TRUE.equals(body.dryRun()) ? "DRY_RUN" : "SUCCEEDED";
        if (!expectedStatus.equals(receipt.get("status")) || !string(receipt.get("beforeResourceVersion")).equals(body.expectedResourceVersion())
                || !string(receipt.get("afterResourceVersion")).equals(version)) throw error(502, "FEATURE_DRIFT_RECEIPT_INVALID");
        String actionId = string(receipt.get("actionId"));
        if (actionId.isBlank()) throw error(502, "FEATURE_DRIFT_RECEIPT_INVALID");
        Instant began; Instant ended;
        try { began = Instant.parse(string(receipt.get("startedAt"))); ended = Instant.parse(string(receipt.get("completedAt"))); }
        catch (Exception invalid) { throw error(502, "FEATURE_DRIFT_RECEIPT_INVALID"); }
        AgentContract.AuditReceipt audit = new AgentContract.AuditReceipt("receipt-" + actionId,
            context.requestId(), context.workspaceId(), context.incidentId(), context.traceId(), context.toolName(),
            context.actorId(), approval.approverId(), body.approvalId(), approval.argumentsDigest(), actionId,
            expectedStatus, string(receipt.get("beforeResourceVersion")), version, began, ended, List.of());
        Map<String, Object> resultData = new LinkedHashMap<>(data);
        resultData.put("actionId", actionId); resultData.put("status", expectedStatus);
        resultData.put("stepId", body.stepId()); resultData.put("planDigest", body.planDigest());
        return AgentContract.successWithReceipt((T) resultData, context, source, version, started, audit);
    }

    /** 保留原始计划字段与已验证人工身份，禁止浏览器自报身份。 / Preserve plan fields and the verified human identity. */
    private Map<String, Object> request(AgentContract.ToolRequest<?> body, AgentContract.RequestContext context,
            GovernedApprovalVerifier.ApprovalDecision approval) {
        Map<String, Object> value = new LinkedHashMap<>(mapper.convertValue(body, new TypeReference<Map<String, Object>>() { }));
        value.put("workspaceId", context.workspaceId()); value.put("incidentId", context.incidentId());
        value.put("traceId", context.traceId()); value.put("actorId", context.actorId());
        value.put("idempotencyKey", context.idempotencyKey());
        if (approval != null) value.put("approverId", approval.approverId());
        return value;
    }

    /** 固定路径请求真实服务，错误和超时绝不进入旧比赛状态。 / Call fixed real-service routes without falling back on errors or timeout. */
    private Map<String, Object> exchange(AgentContract.ToolRequest<?> body, AgentContract.RequestContext context, Map<String, Object> envelope) {
        String root = "/v1/incidents/" + encode(context.incidentId());
        String action = ACTIONS.get(context.toolName());
        String endpoint;
        if (action != null) endpoint = root + "/actions/" + action;
        else {
            String leaf = context.toolName().equals("dataops.dataset.validation.get") ? "dataset"
                : Set.of("mlops.deployment.get", "mlops.release.validation.get").contains(context.toolName()) ? "deployment"
                : Set.of("mlops.inference.probe", "aiops.inference.metrics.get", "aiops.inference.recovery.status").contains(context.toolName()) ? "probe" : "evidence";
            endpoint = root + "/" + leaf + "?workspaceId=" + encode(context.workspaceId()) + "&traceId=" + encode(context.traceId())
                + "&requestId=" + encode(context.requestId()) + "&toolName=" + encode(context.toolName());
            Map<String, Object> args = arguments(body.arguments());
            for (String key : List.of("sampleLimit", "testDatasetRef", "targetRevision")) if (args.get(key) != null) endpoint += "&" + key + "=" + encode(String.valueOf(args.get(key)));
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(endpoint)).header("Accept", "application/json")
                .header("X-Feature-Drift-Token", token).timeout(Duration.ofSeconds(action == null ? 15 : 120));
            if (action == null) builder.GET();
            else builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(envelope), StandardCharsets.UTF_8));
            HttpResponse<java.io.InputStream> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (java.io.InputStream input = response.body()) { bytes = input.readNBytes(2 * 1024 * 1024 + 1); }
            if (bytes.length > 2 * 1024 * 1024) throw error(502, "FEATURE_DRIFT_RESPONSE_TOO_LARGE");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                int status = Set.of(400, 403, 404, 409, 412, 422).contains(response.statusCode()) ? response.statusCode() : 502;
                throw error(status, "FEATURE_DRIFT_RUNTIME_REJECTED");
            }
            return mapper.readValue(bytes, new TypeReference<Map<String, Object>>() { });
        } catch (AgentContractException known) { throw known; }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw error(503, "FEATURE_DRIFT_RUNTIME_INTERRUPTED"); }
        catch (Exception unavailable) { throw error(503, "FEATURE_DRIFT_RUNTIME_UNAVAILABLE"); }
    }

    /** 将DTO转换为域参数，空值保持无匹配。 / Convert typed domain arguments without treating null as a target. */
    private Map<String, Object> arguments(Object value) { return value == null ? Map.of() : mapper.convertValue(value, new TypeReference<Map<String, Object>>() { }); }
    /** 限制路径身份，拒绝路径穿越。 / Bound path identities and reject traversal. */
    private static void requireId(String value) { if (value == null || !value.matches("[A-Za-z0-9_-]{1,160}")) throw error(400, "FEATURE_DRIFT_ID_INVALID"); }
    /** 编码单个查询值。 / Encode one query value. */
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    /** 将JSON标量版本转为文本。 / Convert scalar versions into text. */
    private static String string(Object value) { return value == null ? "" : String.valueOf(value); }
    /** 返回无秘密的固定公共错误。 / Return a fixed public error without secrets. */
    private static AgentContractException error(int status, String code) { return new AgentContractException(status, code, "真实特征漂移运行时未完成本次请求；未回退演练状态。"); }
}
