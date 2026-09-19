/* Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 真实运行时HTTP边界回归 / Real-runtime HTTP boundary regression.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-18
 * Version: 1.3.0 | Security Level: INTERNAL
 * __version__: 1.3.0 | __author__: maoyo | __copyright__: Copyright 2026 Synapxnet
 * __maintainer__: maoyo | __email__: synapxnet@gmail.com
 */
package com.synapxnet.goai.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 校验真实HTTP协议，不调用业务环境。 / Verify real HTTP protocol boundaries without contacting a business environment. */
class FeatureDriftRuntimeClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String TOKEN = "isolated-platform-test-token-0123456789";
    /** 生成已验证的委托身份。 / Build a verified delegation context. */
    private AgentContract.RequestContext context(String tool) {
        return new AgentContract.RequestContext("ws_test", "inc_test", "trace_test", tool, "idempotent_test", "agent_test", "request_test");
    }
    /** 创建固定计划请求。 / Build a fixed governed plan request. */
    private AgentContract.ToolRequest<Map<String, Object>> body(String tool, boolean dry) {
        return new AgentContract.ToolRequest<>("request_test", tool, Map.of("deploymentUid", "deploy_risk_prod", "featureSetUid", "feature_set_risk_fallback_v1", "reasonCode", "UPSTREAM_SDK_CONTRACT_DRIFT"),
            "apr_test", "plan_test", "a".repeat(64), "step_test", "deploy_risk_prod/feature-set", 18L, "42", "b".repeat(64), false, "approved test", "idempotent_test", dry);
    }
    /** 生成实际来源及精确事件响应。 / Build a real-source response scoped to one exact incident. */
    private Map<String, Object> data() {
        return new LinkedHashMap<>(Map.of("sourceMode", "REAL_CPU_SYNTHETIC_STAGING", "workspaceId", "ws_test", "incidentId", "inc_test", "traceId", "trace_test", "synthetic", true, "resourceVersions", Map.of("deploy_risk_prod", "42")));
    }
    /** 用真实本地HTTP服务器检查序列化、鉴权与返回回执。 / Check serialization, authentication and receipts through a real local HTTP server. */
    @Test void approvalPrecedesWriteAndActualReceiptIsPreserved() throws Exception {
        var verifier = mock(GovernedApprovalVerifier.class);
        when(verifier.verify(any(), any())).thenReturn(new GovernedApprovalVerifier.ApprovalDecision("apr_test", "human_verified", "agent_test", "b".repeat(64)));
        AtomicReference<Map<?, ?>> sent = new AtomicReference<>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            assertEquals(TOKEN, exchange.getRequestHeaders().getFirst("X-Feature-Drift-Token"));
            assertEquals("POST", exchange.getRequestMethod()); assertEquals("/v1/incidents/inc_test/actions/fallback", exchange.getRequestURI().getPath());
            Map<?, ?> input = mapper.readValue(exchange.getRequestBody(), Map.class); sent.set(input);
            boolean dry = Boolean.TRUE.equals(input.get("dryRun")); String version = dry ? "42" : "43";
            byte[] output = mapper.writeValueAsBytes(Map.of("data", data(), "resourceVersion", version, "actionReceipt", Map.of("actionId", "real_action", "beforeResourceVersion", "42", "afterResourceVersion", version,
                "status", dry ? "DRY_RUN" : "SUCCEEDED", "startedAt", "2026-09-18T01:00:00Z", "completedAt", "2026-09-18T01:00:01Z")));
            exchange.sendResponseHeaders(200, output.length); exchange.getResponseBody().write(output); exchange.close();
        }); server.start();
        try {
            var client = new FeatureDriftRuntimeClient(true, "http://127.0.0.1:" + server.getAddress().getPort(), TOKEN, "mlops", mapper, verifier, HttpClient.newHttpClient());
            String tool = "mlops.feature.fallback.apply";
            var result = client.invoke(body(tool, false), context(tool));
            assertEquals("human_verified", sent.get().get("approverId")); assertEquals("ws_test", sent.get().get("workspaceId"));
            assertEquals("43", result.meta().resourceVersion()); assertEquals("real_action", result.auditReceipt().actionId());
            assertEquals("human_verified", result.auditReceipt().approverId()); assertEquals("SUCCEEDED", ((Map<?, ?>) result.data()).get("status"));
            var dry = client.invoke(body(tool, true), context(tool)); assertEquals("DRY_RUN", dry.auditReceipt().actionStatus()); assertEquals("42", dry.meta().resourceVersion());
            verify(verifier, times(2)).verify(any(), any());
        } finally { server.stop(0); }
    }
    /** 错误作用域、缺失版本和HTTP拒绝绝不产生成功证据。 / Reject wrong scopes, missing versions and HTTP errors without success evidence. */
    @Test void invalidResponsesFailClosedAndReadsNeverRequestApproval() throws Exception {
        var verifier = mock(GovernedApprovalVerifier.class); AtomicInteger variant = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            assertEquals("GET", exchange.getRequestMethod()); assertTrue(exchange.getRequestURI().getQuery().contains("workspaceId=ws_test"));
            Map<String, Object> responseData = data();
            if (variant.get() == 1) responseData.put("workspaceId", "ws_other");
            if (variant.get() == 2) responseData.put("incidentId", "inc_other");
            if (variant.get() == 3) responseData.put("traceId", "trace_other");
            if (variant.get() == 4) responseData.put("sourceMode", "SIMULATION");
            byte[] output = mapper.writeValueAsBytes(Map.of("data", responseData, "resourceVersion", variant.get() == 5 ? "" : "42"));
            exchange.sendResponseHeaders(variant.get() == 6 ? 409 : 200, output.length); exchange.getResponseBody().write(output); exchange.close();
        }); server.start();
        try {
            var client = new FeatureDriftRuntimeClient(true, "http://127.0.0.1:" + server.getAddress().getPort(), TOKEN, "mlops", mapper, verifier, HttpClient.newHttpClient());
            String tool = "mlops.deployment.get";
            assertTrue(client.invoke(body(tool, false), context(tool)).success());
            for (int i = 1; i <= 6; i++) { variant.set(i); assertThrows(AgentContractException.class, () -> client.invoke(body(tool, false), context(tool))); }
            verifyNoInteractions(verifier);
        } finally { server.stop(0); }
    }
    /** 未批准的写操作不触达网络。 / Never send an unapproved mutation to the network. */
    @Test void deniedApprovalStopsBeforeTransport() {
        var verifier = mock(GovernedApprovalVerifier.class);
        when(verifier.verify(any(), any())).thenThrow(new AgentContractException(403, "APPROVAL_REQUIRED", "Denied"));
        var client = new FeatureDriftRuntimeClient(true, "http://127.0.0.1:1", TOKEN, "mlops", mapper, verifier, HttpClient.newHttpClient());
        var error = assertThrows(AgentContractException.class, () -> client.invoke(body("mlops.feature.fallback.apply", false), context("mlops.feature.fallback.apply")));
        assertEquals("APPROVAL_REQUIRED", error.getCode());
    }
    /** 固定目标和平台阻止跨业务工具调用。 / Fixed targets and platform roles block unrelated tools. */
    @Test void exactRiskTargetsAndDisabledModeDoNotCaptureOtherWorkloads() {
        var verifier = mock(GovernedApprovalVerifier.class);
        var client = new FeatureDriftRuntimeClient(true, "http://127.0.0.1:1", TOKEN, "mlops", mapper, verifier, HttpClient.newHttpClient());
        assertTrue(client.handles("mlops.deployment.get", Map.of("deploymentUid", "deploy_risk_prod")));
        assertFalse(client.handles("mlops.deployment.get", Map.of("deploymentUid", "deploy_quant_ashare_research")));
        assertFalse(client.handles("aiops.service.health", Map.of("serviceUid", "service_risk_inference")));
        assertFalse(new FeatureDriftRuntimeClient(false, "", "", "mlops", mapper, verifier, HttpClient.newHttpClient()).handles("mlops.deployment.get", Map.of("deploymentUid", "deploy_risk_prod")));
        assertThrows(IllegalArgumentException.class, () -> new FeatureDriftRuntimeClient(true, "http://user:pass@127.0.0.1/", TOKEN, "mlops", mapper, verifier, HttpClient.newHttpClient()));
    }
}
