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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Agent 过滤器在控制器执行前返回稳定且脱敏的公共错误包络。
 * English: Maintains explicitly configured parsing for trusted evidence and delegated identity contracts.
 */
class AgentRequestContextFilterTest {

    private static final String SECRET = "goai-filter-test-secret-1234567890";
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /**
     * 验证缺少公共请求头时直接返回 400 JSON，而不是由容器转换为 500。
     *
     * @throws Exception 测试请求执行或 JSON 解析失败时抛出
     * English: Provides the scoped returns structured bad request when headers are missing contract and preserves structured error handling.
     */
    /** 验证受限身份与错误响应契约。 English: Verifies returns structured bad request when headers are missing. English: Provides the scoped returns structured bad request when headers are missing contract and preserves structured error handling. */
    @Test
    void returnsStructuredBadRequestWhenHeadersAreMissing() throws Exception {
        AgentRequestContextFilter filter = createFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/agent/v1/actions/action-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo("INVALID_ARGUMENT");
        assertThat(body.path("meta").path("workspaceId").isNull()).isTrue();
    }

    /**
     * 验证公共请求头有效但未携带委托令牌时返回 401 脱敏包络。
     *
     * @throws Exception 测试请求执行或 JSON 解析失败时抛出
     * English: Provides the scoped returns structured unauthorized when token is missing contract and preserves structured error handling.
     */
    /** 验证受限身份与错误响应契约。 English: Verifies returns structured unauthorized when token is missing. English: Provides the scoped returns structured unauthorized when token is missing contract and preserves structured error handling. */
    @Test
    void returnsStructuredUnauthorizedWhenTokenIsMissing() throws Exception {
        AgentRequestContextFilter filter = createFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/agent/v1/actions/action-1");
        request.addHeader("X-OpenXnet-Workspace-Id", "ws_goai_demo");
        request.addHeader("X-OpenXnet-Incident-Id", "incident-1");
        request.addHeader("X-OpenXnet-Trace-Id", "trace-1");
        request.addHeader("X-OpenXnet-Tool-Name", "mlops.deployment.rollback");
        request.addHeader("Idempotency-Key", "idem-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body.path("error").path("code").asText()).isEqualTo("UNAUTHENTICATED");
        assertThat(response.getContentAsString()).doesNotContain(SECRET);
    }

    /**
     * 创建使用测试密钥和标准受众的过滤器实例。
     *
     * @return 可独立执行的 Agent 请求过滤器
     * English: Provides the scoped create filter contract and preserves structured error handling.
     */
    private AgentRequestContextFilter createFilter() {
        DelegatedTokenVerifier verifier = new DelegatedTokenVerifier(SECRET, "openxnet-agent-adapter");
        return new AgentRequestContextFilter(verifier, objectMapper);
    }
}
