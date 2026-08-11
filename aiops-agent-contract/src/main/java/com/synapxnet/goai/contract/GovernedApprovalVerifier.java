package com.synapxnet.goai.contract;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

/**
 * 对任意三平台写工具执行计划级审批内省，并在摘要或职责边界不一致时失败关闭。
 */
public final class GovernedApprovalVerifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(GovernedApprovalVerifier.class);
    private static final int MIN_TIMEOUT_MILLIS = 250;
    private static final int MAX_TIMEOUT_MILLIS = 10_000;

    private final RestClient client;
    private final String serviceToken;
    private final ObjectMapper canonicalMapper;

    /**
     * 创建审批内省客户端。
     *
     * @param objectMapper Spring JSON 映射器
     * @param baseUrl 审批服务根地址
     * @param serviceToken 内部审批服务令牌
     */
    public GovernedApprovalVerifier(ObjectMapper objectMapper, String baseUrl, String serviceToken) {
        this(objectMapper, baseUrl, serviceToken, 3_000, 5_000);
    }

    /**
     * 创建带有界连接和读取预算的审批内省客户端。
     *
     * @param objectMapper Spring JSON 映射器
     * @param baseUrl 审批服务根地址
     * @param serviceToken 内部审批服务令牌
     * @param connectTimeoutMillis 连接超时毫秒数
     * @param readTimeoutMillis 读取超时毫秒数
     */
    public GovernedApprovalVerifier(
            ObjectMapper objectMapper,
            String baseUrl,
            String serviceToken,
            int connectTimeoutMillis,
            int readTimeoutMillis) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(requireTimeout(connectTimeoutMillis, "connectTimeoutMillis"));
        requestFactory.setReadTimeout(requireTimeout(readTimeoutMillis, "readTimeoutMillis"));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.serviceToken = serviceToken == null ? "" : serviceToken.trim();
        this.canonicalMapper = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    /**
     * 校验工具参数摘要和远程审批范围。
     *
     * @param request 完整工具请求
     * @param context 已鉴权调用上下文
     * @return 审批人、执行人和参数摘要
     */
    public ApprovalDecision verify(
            AgentContract.ToolRequest<?> request,
            AgentContract.RequestContext context) {
        requireGovernance(request, context);
        String computedDigest = digestArguments(request.arguments());
        if (!computedDigest.equals(request.argumentsDigest())) {
            throw new AgentContractException(403, "APPROVAL_SCOPE_MISMATCH", "工具参数与审批摘要不一致");
        }
        if (serviceToken.length() < 32) {
            throw new AgentContractException(503, "UPSTREAM_UNAVAILABLE", "审批服务身份未配置");
        }
        try {
            IntrospectionResponse response = client.post()
                    .uri("/api/v1/approvals/{approvalId}/introspect", request.approvalId())
                    .headers(headers -> headers.setBearerAuth(serviceToken))
                    .body(new IntrospectionRequest(
                            context.workspaceId(), context.incidentId(), context.traceId(),
                            request.planId(), request.planDigest(), request.stepId(), context.toolName(),
                            request.resourceId(), request.targetRevision(), request.argumentsDigest(),
                            request.expectedResourceVersion(), context.actorId(),
                            Boolean.TRUE.equals(request.compensation())))
                    .retrieve()
                    .body(IntrospectionResponse.class);
            requireValid(response, request, context);
            return new ApprovalDecision(
                    request.approvalId(), response.approverId(), context.actorId(), computedDigest);
        } catch (AgentContractException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            LOGGER.warn("审批内省返回非成功状态：status={}", exception.getStatusCode().value());
            throw new AgentContractException(
                    503, "UPSTREAM_UNAVAILABLE", "审批服务暂时不可用，受控操作已拒绝", true,
                    Map.of("upstreamStatus", exception.getStatusCode().value()));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "审批内省传输或解析失败：exceptionType={}, rootCauseType={}",
                    exception.getClass().getSimpleName(), rootCauseType(exception));
            throw new AgentContractException(
                    503, "UPSTREAM_UNAVAILABLE", "审批服务暂时不可用，受控操作已拒绝", true, Map.of());
        }
    }

    /**
     * 校验审批服务网络超时处于可控范围，避免配置为无限等待或过大预算。
     *
     * @param value 超时毫秒数
     * @param field 配置字段名称
     * @return 已校验超时毫秒数
     */
    private static int requireTimeout(int value, String field) {
        if (value < MIN_TIMEOUT_MILLIS || value > MAX_TIMEOUT_MILLIS) {
            throw new IllegalArgumentException(field + " 超出允许范围");
        }
        return value;
    }

    /**
     * 提取异常链最深层的类型名称，仅用于不包含 URL、凭据和正文的安全诊断。
     *
     * @param exception 审批调用异常
     * @return 根因异常类型名称
     */
    private static String rootCauseType(RuntimeException exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName();
    }

    /**
     * 校验本地治理字段的完整性和幂等范围。
     *
     * @param request 工具请求
     * @param context 已鉴权上下文
     */
    private void requireGovernance(
            AgentContract.ToolRequest<?> request,
            AgentContract.RequestContext context) {
        String[] values = {
                request.approvalId(), request.planId(), request.planDigest(), request.stepId(),
                request.resourceId(), request.expectedResourceVersion(), request.argumentsDigest(),
                request.reason(), request.idempotencyKey(),
        };
        for (String value : values) {
            if (value == null || value.isBlank() || value.length() > 512 || value.indexOf('\0') >= 0) {
                throw new AgentContractException(403, "APPROVAL_REQUIRED", "写操作缺少完整计划级审批范围");
            }
        }
        if (request.targetRevision() == null || request.targetRevision() < 1
                || request.compensation() == null || request.dryRun() == null
                || !request.idempotencyKey().equals(context.idempotencyKey())
                || !request.planDigest().matches("[a-f0-9]{64}")
                || !request.argumentsDigest().matches("[a-f0-9]{64}")) {
            throw new AgentContractException(403, "APPROVAL_SCOPE_MISMATCH", "写操作治理字段无效");
        }
    }

    /**
     * 严格比对审批内省回执和当前调用。
     *
     * @param response 审批内省结果
     * @param request 工具请求
     * @param context 已鉴权上下文
     */
    private void requireValid(
            IntrospectionResponse response,
            AgentContract.ToolRequest<?> request,
            AgentContract.RequestContext context) {
        boolean valid = response != null && response.active() && "APPROVED".equals(response.status())
                && response.expiresAt() != null && response.expiresAt().isAfter(Instant.now())
                && context.workspaceId().equals(response.workspaceId())
                && context.incidentId().equals(response.incidentId())
                && context.traceId().equals(response.traceId())
                && request.planId().equals(response.planId())
                && request.planDigest().equals(response.planDigest())
                && request.stepId().equals(response.stepId())
                && context.toolName().equals(response.toolName())
                && request.resourceId().equals(response.resourceId())
                && request.targetRevision().equals(response.targetRevision())
                && request.argumentsDigest().equals(response.argumentsDigest())
                && request.expectedResourceVersion().equals(response.expectedResourceVersion())
                && Boolean.TRUE.equals(request.compensation()) == response.compensation()
                && response.requesterId() != null
                && response.approverId() != null
                && !response.requesterId().equals(context.actorId())
                && !response.approverId().equals(context.actorId());
        if (!valid) {
            throw new AgentContractException(403, "APPROVAL_INVALID", "审批已失效、范围不匹配或不满足职责分离");
        }
    }

    /**
     * 生成按对象键排序的领域参数 SHA-256。
     *
     * @param arguments 领域参数 DTO
     * @return 小写十六进制摘要
     */
    String digestArguments(Object arguments) {
        try {
            byte[] json = canonicalMapper.writeValueAsString(arguments).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (Exception exception) {
            throw new AgentContractException(500, "INTERNAL_ERROR", "无法生成工具参数摘要");
        }
    }

    /**
     * 表示通过内省确认的职责分离结果。
     *
     * @param approvalId 审批标识
     * @param approverId 审批人
     * @param executorId 执行人
     * @param argumentsDigest 参数摘要
     */
    public record ApprovalDecision(
            String approvalId,
            String approverId,
            String executorId,
            String argumentsDigest) {
    }

    /**
     * 表示发送给审批服务的精确步骤上下文。
     */
    private record IntrospectionRequest(
            String workspaceId,
            String incidentId,
            String traceId,
            String planId,
            String planDigest,
            String stepId,
            String toolName,
            String resourceId,
            Long targetRevision,
            String argumentsDigest,
            String expectedResourceVersion,
            String executorId,
            boolean compensation) {
    }

    /**
     * 表示审批服务返回的精确步骤内省结果。
     */
    private record IntrospectionResponse(
            boolean active,
            String status,
            Instant expiresAt,
            String workspaceId,
            String incidentId,
            String traceId,
            String planId,
            String planDigest,
            String stepId,
            String toolName,
            String resourceId,
            Long targetRevision,
            String argumentsDigest,
            String expectedResourceVersion,
            boolean compensation,
            String requesterId,
            String approverId) {
    }
}
