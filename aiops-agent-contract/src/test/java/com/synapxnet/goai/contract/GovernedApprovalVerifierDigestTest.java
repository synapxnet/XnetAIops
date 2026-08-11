package com.synapxnet.goai.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 Java 平台参数摘要与 OpenXnet 稳定 JSON 摘要完全一致。
 */
class GovernedApprovalVerifierDigestTest {

    /**
     * 校验字段声明顺序不同于字母顺序时仍生成相同摘要。
     */
    @Test
    void producesOpenXnetCompatibleStableDigest() {
        GovernedApprovalVerifier verifier = new GovernedApprovalVerifier(
                new ObjectMapper(), "http://127.0.0.1:8080", "x".repeat(32));

        String digest = verifier.digestArguments(new FallbackArguments(
                "INPUT_CONTRACT_DRIFT", "feature_set_risk_fallback_v1", "deploy_risk_prod"));

        assertEquals("e177fcee6ab25907b0831045585e75990524f1095e5ac2bc6ad65bc6a5587be2", digest);
    }

    /**
     * 校验审批客户端可以解析真实服务格式，并返回严格匹配的审批职责信息。
     */
    @Test
    void verifiesMatchingApprovalThroughHttpService() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/approvals/apr_test/introspect", exchange -> {
            byte[] response = matchingIntrospectionResponse().getBytes(StandardCharsets.UTF_8);
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            GovernedApprovalVerifier verifier = new GovernedApprovalVerifier(
                    new ObjectMapper().findAndRegisterModules(),
                    "http://127.0.0.1:" + server.getAddress().getPort(), "x".repeat(32), 1_000, 1_000);
            FallbackArguments arguments = new FallbackArguments(
                    "INPUT_CONTRACT_DRIFT", "feature_set_risk_fallback_v1", "deploy_risk_prod");
            String argumentsDigest = verifier.digestArguments(arguments);
            AgentContract.ToolRequest<FallbackArguments> request = new AgentContract.ToolRequest<>(
                    "req_test", "mlops.feature.fallback.apply", arguments, "apr_test", "plan_test",
                    "a".repeat(64), "fallback-apply", "deploy_risk_prod", 17L, "42", argumentsDigest,
                    false, "测试审批内省", "idem_test", true);
            AgentContract.RequestContext context = new AgentContract.RequestContext(
                    "ws_goai_demo", "inc_test", "trace_test", "mlops.feature.fallback.apply",
                    "idem_test", "enterprise-goai:operator", "req_test");

            GovernedApprovalVerifier.ApprovalDecision decision = verifier.verify(request, context);

            assertEquals("enterprise-goai:approver", decision.approverId());
            assertEquals(argumentsDigest, decision.argumentsDigest());
        } finally {
            server.stop(0);
        }
    }

    /**
     * 校验超时预算不能被配置成近似无限等待或无效的极短值。
     */
    @Test
    void rejectsTimeoutOutsideBoundedBudget() {
        assertThrows(IllegalArgumentException.class, () -> new GovernedApprovalVerifier(
                new ObjectMapper(), "http://127.0.0.1:8080", "x".repeat(32), 0, 20_000));
    }

    /**
     * 构造与测试请求完全匹配且不含敏感数据的审批内省响应。
     *
     * @return 审批服务 JSON 响应
     */
    private String matchingIntrospectionResponse() {
        return """
                {
                  "active": true,
                  "status": "APPROVED",
                  "expiresAt": "%s",
                  "workspaceId": "ws_goai_demo",
                  "incidentId": "inc_test",
                  "traceId": "trace_test",
                  "planId": "plan_test",
                  "planDigest": "%s",
                  "stepId": "fallback-apply",
                  "toolName": "mlops.feature.fallback.apply",
                  "resourceId": "deploy_risk_prod",
                  "targetRevision": 17,
                  "argumentsDigest": "%s",
                  "expectedResourceVersion": "42",
                  "compensation": false,
                  "requesterId": "enterprise-goai:investigator",
                  "approverId": "enterprise-goai:approver"
                }
                """.formatted(
                Instant.now().plusSeconds(60), "a".repeat(64),
                "e177fcee6ab25907b0831045585e75990524f1095e5ac2bc6ad65bc6a5587be2");
    }

    /**
     * 使用非字母序字段声明模拟真实领域 DTO。
     */
    private record FallbackArguments(
            String reasonCode,
            String featureSetUid,
            String deploymentUid) {
    }
}
