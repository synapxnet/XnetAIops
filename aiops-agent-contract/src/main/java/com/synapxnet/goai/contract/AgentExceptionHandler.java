package com.synapxnet.goai.contract;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * 将预期契约异常转换为公共错误包络，并避免向客户端泄露堆栈和凭据。
 */
@RestControllerAdvice
final class AgentExceptionHandler {

    /**
     * 转换明确的契约、鉴权和治理异常。
     *
     * @param exception 公共契约异常
     * @param request 当前请求
     * @return 带正确 HTTP 状态的失败包络
     */
    @ExceptionHandler(AgentContractException.class)
    ResponseEntity<AgentContract.ToolResponse<Void>> handle(
            AgentContractException exception,
            HttpServletRequest request) {
        AgentContract.RequestContext context = requestContext(request);
        AgentContract.ToolMeta meta = new AgentContract.ToolMeta(
                context.requestId(), context.workspaceId(), context.incidentId(), context.traceId(),
                context.toolName(), AgentContract.CONTRACT_VERSION, Instant.now(), 0L,
                "agent-contract", null, null);
        AgentContract.ToolError error = new AgentContract.ToolError(
                exception.getCode(), exception.getMessage(), exception.isRetryable(), exception.getDetails());
        AgentContract.ToolResponse<Void> response = new AgentContract.ToolResponse<>(false, null, error, meta, null);
        return ResponseEntity.status(exception.getHttpStatus()).body(response);
    }

    /**
     * 获取请求上下文；过滤器前失败时返回不含伪造业务 ID 的空上下文。
     *
     * @param request 当前请求
     * @return 可安全序列化的上下文
     */
    private AgentContract.RequestContext requestContext(HttpServletRequest request) {
        Object value = request.getAttribute(AgentContract.CONTEXT_ATTRIBUTE);
        if (value instanceof AgentContract.RequestContext context) {
            return context;
        }
        return new AgentContract.RequestContext(null, null, null, null, null, null, null);
    }
}
