/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 运行证据与受限身份对接。 Operations evidence and scoped identity integration.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-13
 * Version: 1.0.0 | Security Level: INTERNAL
 * Maintainer: maoyo | Email: synapxnet@gmail.com
 * Restored selectively from the SynapXnet competition baseline b260b27.
 */
package com.synapxnet.goai.contract;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * 将预期契约异常转换为公共错误包络，并避免向客户端泄露堆栈和凭据。
 * English: Maps contract failures to a public ToolResponse without exposing credentials or stack traces.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
final class AgentExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentExceptionHandler.class);

    /**
     * 转换明确的契约、鉴权和治理异常。
     *
     * @param exception 公共契约异常
     * @param request 当前请求
     * @return 带正确 HTTP 状态的失败包络
     * English: Maps contract failures to a public ToolResponse without exposing credentials or stack traces.
     */
    @ExceptionHandler(AgentContractException.class)
    ResponseEntity<AgentContract.ToolResponse<Void>> handle(
            AgentContractException exception,
            HttpServletRequest request) {
        AgentContract.RequestContext context = requestContext(request);
        LOGGER.warn(
                "Agent 工具请求被安全拒绝：code={}, status={}, toolName={}",
                exception.getCode(), exception.getHttpStatus(), context.toolName());
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
     * English: Reads verified trace context for consistent error attribution.
     */
    private AgentContract.RequestContext requestContext(HttpServletRequest request) {
        Object value = request.getAttribute(AgentContract.CONTEXT_ATTRIBUTE);
        if (value instanceof AgentContract.RequestContext context) {
            return context;
        }
        return new AgentContract.RequestContext(null, null, null, null, null, null, null);
    }
}
