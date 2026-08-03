package com.synapxnet.goai.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 定义 GOAI 1.0.0 工具调用的稳定请求、响应、证据和审计数据结构。
 */
public final class AgentContract {

    public static final String CONTRACT_VERSION = "1.0.0";
    public static final String CONTEXT_ATTRIBUTE = AgentContract.class.getName() + ".context";

    /**
     * 阻止工具类被实例化。
     */
    private AgentContract() {
    }

    /**
     * 从已验证请求中读取 Agent 上下文，并校验路径、Header 与请求体工具名一致。
     *
     * @param request 当前 HTTP 请求
     * @param expectedToolName Controller 对应的固定工具名
     * @param body 已完成 JSON 反序列化的工具请求
     * @return 已由安全过滤器验证的调用上下文
     */
    public static RequestContext requireContext(
            HttpServletRequest request,
            String expectedToolName,
            ToolRequest<?> body) {
        Object value = request.getAttribute(CONTEXT_ATTRIBUTE);
        if (!(value instanceof RequestContext context)) {
            throw new AgentContractException(401, "UNAUTHENTICATED", "缺少已验证的 Agent 请求上下文");
        }
        if (body == null || body.toolName() == null || !expectedToolName.equals(body.toolName())) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "请求体 toolName 与目标工具不一致");
        }
        if (!expectedToolName.equals(context.toolName())) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "请求头 toolName 与目标工具不一致");
        }
        if (body.requestId() == null || body.requestId().isBlank() || body.requestId().length() > 128) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "requestId 缺失或格式无效");
        }
        return context.withRequestId(body.requestId());
    }

    /**
     * 创建符合公共包络的成功响应。
     *
     * @param data 结构化领域证据或动作结果
     * @param context 已验证的调用上下文
     * @param source 领域证据来源
     * @param resourceVersion 可选资源版本
     * @param startedNanos 调用开始时的单调时钟值
     * @param <T> 领域响应类型
     * @return 成功工具响应
     */
    public static <T> ToolResponse<T> success(
            T data,
            RequestContext context,
            String source,
            String resourceVersion,
            long startedNanos) {
        Instant observedAt = Instant.now();
        String evidenceId = EvidenceDigest.create(source, data, resourceVersion, observedAt);
        ToolMeta meta = new ToolMeta(
                context.requestId(), context.workspaceId(), context.incidentId(), context.traceId(),
                context.toolName(), CONTRACT_VERSION, observedAt,
                Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L), source,
                evidenceId, resourceVersion);
        return new ToolResponse<>(true, data, null, meta, null);
    }

    /**
     * 创建包含审计回执的写工具成功响应。
     *
     * @param data 结构化动作结果
     * @param context 已验证的调用上下文
     * @param source 动作所属领域来源
     * @param resourceVersion 动作受理后的资源版本
     * @param startedNanos 调用开始时的单调时钟值
     * @param receipt 不可歧义的审计回执
     * @param <T> 动作响应类型
     * @return 写工具成功响应
     */
    public static <T> ToolResponse<T> successWithReceipt(
            T data,
            RequestContext context,
            String source,
            String resourceVersion,
            long startedNanos,
            AuditReceipt receipt) {
        ToolResponse<T> response = success(data, context, source, resourceVersion, startedNanos);
        return new ToolResponse<>(true, response.data(), null, response.meta(), receipt);
    }

    /**
     * 表示公共工具请求包络，arguments 保持具体泛型以禁止自由 Map 穿透领域层。
     *
     * @param requestId 全局唯一请求编号
     * @param toolName 固定工具名
     * @param arguments 工具专用参数
     * @param approvalId 写操作审批引用
     * @param expectedResourceVersion 写操作预期资源版本
     * @param reason 写操作原因
     * @param dryRun 是否只执行预检
     * @param <T> 参数 DTO 类型
     */
    public record ToolRequest<T>(
            String requestId,
            String toolName,
            T arguments,
            String approvalId,
            String expectedResourceVersion,
            String reason,
            Boolean dryRun) {
    }

    /**
     * 表示公共工具响应包络。
     *
     * @param success 调用是否成功
     * @param data 成功数据
     * @param error 结构化错误
     * @param meta 调用与证据元数据
     * @param auditReceipt 写工具审计回执
     * @param <T> 领域响应类型
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ToolResponse<T>(
            boolean success,
            T data,
            ToolError error,
            ToolMeta meta,
            AuditReceipt auditReceipt) {
    }

    /**
     * 表示可供调用方判断重试策略的结构化错误。
     */
    public record ToolError(String code, String message, boolean retryable, Map<String, Object> details) {
    }

    /**
     * 表示工具调用、Trace 和证据的公共元数据。
     */
    public record ToolMeta(
            String requestId,
            String workspaceId,
            String incidentId,
            String traceId,
            String toolName,
            String contractVersion,
            Instant observedAt,
            long durationMs,
            String source,
            String evidenceId,
            String resourceVersion) {
    }

    /**
     * 表示由受控写操作产生的追加式审计回执。
     */
    public record AuditReceipt(
            String receiptId,
            String requestId,
            String workspaceId,
            String incidentId,
            String traceId,
            String toolName,
            String actorId,
            String approverId,
            String approvalId,
            String requestDigest,
            String actionId,
            String actionStatus,
            String beforeResourceVersion,
            String afterResourceVersion,
            Instant startedAt,
            Instant completedAt,
            List<String> evidenceIds) {
    }

    /**
     * 表示从委托令牌和公共 Header 解析出的可信调用上下文。
     */
    public record RequestContext(
            String workspaceId,
            String incidentId,
            String traceId,
            String toolName,
            String idempotencyKey,
            String actorId,
            String requestId) {

        /**
         * 将请求体中的全局 requestId 合入不可变上下文。
         *
         * @param value 已校验的 requestId
         * @return 包含 requestId 的新上下文
         */
        public RequestContext withRequestId(String value) {
            return new RequestContext(
                    workspaceId, incidentId, traceId, toolName, idempotencyKey, actorId, value);
        }
    }
}
