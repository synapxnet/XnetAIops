/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 验证身份、资源和模式隔离。 Verifies identity, resource and execution-mode isolation.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-13 | Version: 1.0.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.goai.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

class OperationsReadAccessTest {
    private static final String GRANTS = """
            [{"tenantUid":"tenant-a","deptUid":"dept-a","teamUid":"team-a","workspaceId":"workspace-a",
              "resources":[{"id":"alert-one","label":"测试告警","kind":"alert","resourceUid":"source-alert",
              "executionMode":"live"}]}]
            """;

    /** 缺配置时不请求网络且保持不可用。 Missing configuration fails closed before any network request. */
    @Test
    void rejectsUnconfiguredScope() {
        OperationsReadAccess access = new OperationsReadAccess(new ObjectMapper(), "", "[]");
        assertEquals("OPERATIONS_SCOPE_NOT_CONFIGURED",
                assertThrows(AgentContractException.class, () -> access.authorizeWeb(request(), "alert")).getCode());
    }

    /** 经真实HTTP验证返回的204才允许读取配置目录。 Only a successful HTTP membership check unlocks the configured catalog. */
    @Test
    void validatesIdentityThenReturnsOnlyGrantedResources() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(204, calls);
        try {
            OperationsReadAccess access = access(server);
            OperationsReadAccess.AuthorizedScope scope = access.authorizeWeb(request(), "alert");
            assertEquals(1, calls.get());
            assertEquals("workspace-a", scope.workspaceId());
            assertEquals("source-alert", scope.resources().get(0).resourceUid());
            assertEquals("live", scope.resources().get(0).executionMode());
            assertEquals("RESOURCE_NOT_FOUND",
                    assertThrows(AgentContractException.class, () -> access.find(scope, "another-tenant-alert")).getCode());
            assertTrue(access.authorizeWeb(request(), "workload").resources().isEmpty());
        } finally {
            server.stop(0);
        }
    }

    /** 身份拒绝与未知组织均不能获得目录。 Denied membership and unknown organizations cannot obtain a resource catalog. */
    @Test
    void refusesDeniedMembership() throws Exception {
        HttpServer server = server(403, new AtomicInteger());
        try {
            assertEquals("PERMISSION_DENIED", assertThrows(AgentContractException.class,
                    () -> access(server).authorizeWeb(request(), "alert")).getCode());
        } finally {
            server.stop(0);
        }
    }

    /** 非204响应不能冒充已认证。 Non-204 identity responses must never count as successful authentication. */
    @Test
    void refusesUnexpectedIdentitySuccessBody() throws Exception {
        HttpServer server = server(200, new AtomicInteger());
        try {
            assertEquals("IDENTITY_SERVICE_UNAVAILABLE", assertThrows(AgentContractException.class,
                    () -> access(server).authorizeWeb(request(), "alert")).getCode());
        } finally {
            server.stop(0);
        }
    }

    /** 已验证Agent身份仍受具体资源约束。 Verified Agent identities still require exact resource authorization. */
    @Test
    void enforcesAgentWorkspaceAndResource() {
        OperationsReadAccess access = new OperationsReadAccess(new ObjectMapper(), "", GRANTS);
        access.requireAgentResource("workspace-a", "alert", "source-alert");
        assertThrows(AgentContractException.class, () -> access.requireAgentResource("workspace-b", "alert", "source-alert"));
        assertThrows(AgentContractException.class, () -> access.requireAgentResource("workspace-a", "alert", "other"));
    }

    /** 配置不明确模式或含通配符时拒绝启动该适配。 Missing modes and wildcard grants are rejected during configuration. */
    @Test
    void requiresExplicitLiveModeAndExactIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> new OperationsReadAccess(new ObjectMapper(), "", GRANTS.replace("\"live\"", "null")));
        assertThrows(IllegalArgumentException.class,
                () -> new OperationsReadAccess(new ObjectMapper(), "", GRANTS.replace("source-alert", "*")));
    }

    /** 重复工作区不能合并不同组织的权限。 Duplicate workspace mappings cannot combine grants from different organizations. */
    @Test
    void rejectsDuplicateWorkspaceAcrossOrganizations() {
        String other = GRANTS.replace("tenant-a", "tenant-b").trim();
        String merged = GRANTS.trim().substring(0, GRANTS.trim().length() - 1) + "," + other.substring(1);
        assertThrows(IllegalArgumentException.class, () -> new OperationsReadAccess(new ObjectMapper(), "", merged));
    }

    /** 数据库时间仅在显式配置时区后获得UTC含义。 Database timestamps gain UTC meaning only after an explicit timezone is configured. */
    @Test
    void preservesUnknownTimezoneAndConvertsConfiguredTimezone() {
        java.time.LocalDateTime value = java.time.LocalDateTime.parse("2026-09-13T12:00:00");
        assertNull(new OperationsReadAccess(new ObjectMapper(), "", GRANTS).databaseInstant(value));
        assertEquals(java.time.Instant.parse("2026-09-13T04:00:00Z"),
                new OperationsReadAccess(new ObjectMapper(), "", GRANTS, "Asia/Shanghai").databaseInstant(value));
    }

    /** 创建本机身份服务，测试仅使用临时令牌。 Creates a loopback identity server using test-only credentials. */
    private HttpServer server(int status, AtomicInteger calls) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // 仅模拟身份服务响应并检查组织上下文。 Simulate the identity response and verify forwarded organization context.
        server.createContext("/access", exchange -> {
            calls.incrementAndGet();
            assertEquals("Bearer test-only", exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals("team-a", exchange.getRequestHeaders().getFirst("X-Team-Uid"));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    /** 将读取适配绑定到临时本机身份服务。 Binds the reader to the temporary loopback identity service. */
    private OperationsReadAccess access(HttpServer server) {
        return new OperationsReadAccess(new ObjectMapper(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/access", GRANTS);
    }

    /** 创建没有生产凭据的组织请求。 Creates a scoped request without production credentials. */
    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer test-only");
        request.addHeader("X-Tenant-Uid", "tenant-a");
        request.addHeader("X-Dept-Uid", "dept-a");
        request.addHeader("X-Team-Uid", "team-a");
        return request;
    }
}
