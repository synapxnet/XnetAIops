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
 */
class AgentRequestContextFilterTest {

    private static final String SECRET = "goai-filter-test-secret-1234567890";
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /**
     * 验证缺少公共请求头时直接返回 400 JSON，而不是由容器转换为 500。
     *
     * @throws Exception 测试请求执行或 JSON 解析失败时抛出
     */
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
     */
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
     */
    private AgentRequestContextFilter createFilter() {
        DelegatedTokenVerifier verifier = new DelegatedTokenVerifier(SECRET, "openxnet-agent-adapter");
        return new AgentRequestContextFilter(verifier, objectMapper);
    }
}
