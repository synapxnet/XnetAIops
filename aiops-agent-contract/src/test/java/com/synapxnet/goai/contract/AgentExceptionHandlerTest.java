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

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证公共 Agent 异常处理器不会被各服务的兜底异常处理器抢占。
 * English: Provides the scoped uses highest advice precedence contract and preserves structured error handling.
 */
class AgentExceptionHandlerTest {

    /** 确认契约异常处理器始终使用 Spring 最高优先级。 English: Provides the scoped uses highest advice precedence contract and preserves structured error handling. */
    /** 验证受限身份与错误响应契约。 English: Verifies uses highest advice precedence. English: Provides the scoped uses highest advice precedence contract and preserves structured error handling. */
    @Test
    void usesHighestAdvicePrecedence() {
        Order order = AgentExceptionHandler.class.getAnnotation(Order.class);

        assertNotNull(order);
        assertEquals(Ordered.HIGHEST_PRECEDENCE, order.value());
    }

    /**
     * 校验控制器校验失败后，错误包络仍保留请求体中的全局 requestId。
     * English: Provides the scoped preserves body request id for structured controller errors contract and preserves structured error handling.
     */
    /** 验证受限身份与错误响应契约。 English: Verifies preserves body request id for structured controller errors. English: Provides the scoped preserves body request id for structured controller errors contract and preserves structured error handling. */
    @Test
    void preservesBodyRequestIdForStructuredControllerErrors() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AgentContract.CONTEXT_ATTRIBUTE, new AgentContract.RequestContext(
                "ws_goai_demo", "inc_test", "trace_test", "aiops.gpu.capacity.ensure",
                "idem_test", "enterprise-goai:operator", null));
        AgentContract.ToolRequest<Map<String, Object>> body = new AgentContract.ToolRequest<>(
                "req_test", "aiops.gpu.capacity.ensure", Map.of(), null, null, null, null,
                null, null, null, null, null, null, null, false);

        AgentContract.requireContext(request, "aiops.gpu.capacity.ensure", body);
        var response = new AgentExceptionHandler().handle(
                new AgentContractException(403, "APPROVAL_REQUIRED", "审批缺失"), request);

        assertEquals("req_test", response.getBody().meta().requestId());
    }
}
