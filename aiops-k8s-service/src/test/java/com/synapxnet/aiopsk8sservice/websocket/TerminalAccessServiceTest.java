/*
Copyright (C) 2026 Synapxnet. All rights reserved.
This file is Synapxnet Proprietary and Confidential. It is strictly
forbidden to copy, distribute, or use without explicit authorization.
Author: maoyo | Department: 研发部 | Date: 2026-09-13
Version: 1.0.0 | Security Level: INTERNAL
__version__: 1.0.0 | __author__: maoyo | __copyright__: Copyright 2026 Synapxnet
__maintainer__: maoyo | __email__: synapxnet@gmail.com
*/

package com.synapxnet.aiopsk8sservice.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.synapxnet.aiopsk8sservice.controller.TerminalTicketController;
import com.synapxnet.aiopsk8sservice.service.K8sClientFactory;
import com.synapxnet.goai.contract.AgentContractException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TerminalAccessServiceTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Clock clock = mock(Clock.class);
    private final AtomicInteger membershipStatus = new AtomicInteger(204);
    private final AtomicInteger identityStatus = new AtomicInteger(200);
    private final AtomicInteger observedCalls = new AtomicInteger();
    private HttpServer identityServer;
    private TerminalAccessService access;
    private String baseUrl;
    private final TerminalAccessService.Target target = new TerminalAccessService.Target(1L, "team", "pod-one", "app");
    private final Instant now = Instant.parse("2026-09-13T12:00:00Z");

    /** 建立本机USR契约夹具，不启动任何集群或真实平台。Start a local USR contract fixture without starting a cluster or real platform. */
    @BeforeEach
    void setup() throws Exception {
        when(clock.instant()).thenReturn(now);
        identityServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        identityServer.createContext("/organization-access", exchange -> {
            observedCalls.incrementAndGet();
            boolean matching = "Bearer fixture-jwt".equals(exchange.getRequestHeaders().getFirst("Authorization"))
                    && "tenant".equals(exchange.getRequestHeaders().getFirst("X-Tenant-Uid"))
                    && "dept".equals(exchange.getRequestHeaders().getFirst("X-Dept-Uid"))
                    && "team".equals(exchange.getRequestHeaders().getFirst("X-Team-Uid"));
            exchange.sendResponseHeaders(matching ? membershipStatus.get() : 403, -1); exchange.close();
        });
        identityServer.createContext("/user/info", exchange -> {
            observedCalls.incrementAndGet();
            byte[] body = "{\"code\":0,\"data\":{\"userId\":\"7\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(identityStatus.get(), body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        identityServer.start();
        baseUrl = "http://127.0.0.1:" + identityServer.getAddress().getPort();
        access = service("https://aiops.example.test", grants("7", target));
    }

    /** 每例停止本地身份夹具。Stop the local identity fixture after each case. */
    @AfterEach
    void cleanup() { identityServer.stop(0); }

    /** 生成明确用户、组织、项目和容器的执行授权。Build an execution grant for an explicit user, organization, project and container. */
    private String grants(String userId, TerminalAccessService.Target target) throws Exception {
        return mapper.writeValueAsString(List.of(new TerminalAccessService.ExecutionGrant(userId, "tenant", "dept", "team", "project-one", "terminal:exec", target)));
    }

    /** 创建使用测试时钟与本机身份服务的授权边界。Create an access boundary using the test clock and local identity service. */
    private TerminalAccessService service(String origins, String grants) {
        return new TerminalAccessService(mapper, baseUrl + "/organization-access", baseUrl + "/user/info", origins, grants, HttpClient.newHttpClient(), clock);
    }

    /** 生成不含真实凭据的已选组织请求。Build a selected-organization request containing no real credentials. */
    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer fixture-jwt");
        request.addHeader("Origin", "https://aiops.example.test");
        request.addHeader("X-Tenant-Uid", "tenant"); request.addHeader("X-Dept-Uid", "dept"); request.addHeader("X-Team-Uid", "team");
        return request;
    }

    /** 有效票据只能使用一次且会话属性不保留JWT。A valid ticket works once and leaves no JWT in session attributes. */
    @Test
    void issuesAndConsumesAnExactSingleUseTicket() {
        var ticket = access.issue(request(), target);
        assertEquals(now.plusSeconds(30), ticket.expiresAt());
        assertEquals("project-one", ticket.workspaceId());
        assertFalse(ticket.webSocketPath().contains(ticket.ticket()));
        var authorized = access.consume(ticket.ticket(), "https://aiops.example.test", target.path());
        assertEquals("7", authorized.userId()); assertNull(authorized.authorization());
        assertFalse(authorized.toString().contains("fixture-jwt"));
        assertThrows(AgentContractException.class, () -> access.consume(ticket.ticket(), "https://aiops.example.test", target.path()));
        assertEquals(4, observedCalls.get());
    }

    /** 未配置、伪造用户ID、错误组织和容器均不能取得执行票据。Unconfigured access, forged user IDs, wrong organizations and containers cannot obtain execution tickets. */
    @Test
    void rejectsUnconfiguredAndUnauthorizedExecution() throws Exception {
        assertEquals(503, assertThrows(AgentContractException.class, () -> service("", "[]").issue(request(), target)).getHttpStatus());
        var forged = request(); forged.addHeader("X-User-Id", "99");
        assertEquals(403, assertThrows(AgentContractException.class, () -> service("https://aiops.example.test", grants("99", target)).issue(forged, target)).getHttpStatus());
        var wrongTeam = request(); wrongTeam.removeHeader("X-Team-Uid"); wrongTeam.addHeader("X-Team-Uid", "other");
        assertThrows(AgentContractException.class, () -> access.issue(wrongTeam, target));
        assertThrows(AgentContractException.class, () -> access.issue(request(), new TerminalAccessService.Target(1L,"team","pod-one","other")));
        assertThrows(AgentContractException.class, () -> access.issue(request(), new TerminalAccessService.Target(2L,"team","pod-one","app")));
    }

    /** 过期、跨目标、缺失与错误Origin票据均不能握手。Expired, cross-target, absent and wrong-Origin tickets cannot handshake. */
    @Test
    void rejectsExpiredAndMismatchedTickets() {
        var first = access.issue(request(), target);
        assertThrows(AgentContractException.class, () -> access.consume(first.ticket(), "https://other.example.test", target.path()));
        assertThrows(AgentContractException.class, () -> access.consume(first.ticket(), "https://aiops.example.test", target.path() + "-other"));
        assertThrows(AgentContractException.class, () -> access.consume(first.ticket(), "https://aiops.example.test", target.path()));
        var second = access.issue(request(), target);
        when(clock.instant()).thenReturn(now.plusSeconds(30));
        assertThrows(AgentContractException.class, () -> access.consume(second.ticket(), "https://aiops.example.test", target.path()));
        assertThrows(AgentContractException.class, () -> access.consume(null, "https://aiops.example.test", target.path()));
    }

    /** 签发后退出或身份服务不可用时，握手再次校验并拒绝。Recheck and reject handshakes after logout or identity-service failure. */
    @Test
    void rechecksRevocationAndFailsClosedOnIdentityErrors() {
        var ticket = access.issue(request(), target); membershipStatus.set(403);
        assertEquals(403, assertThrows(AgentContractException.class, () -> access.consume(ticket.ticket(), "https://aiops.example.test", target.path())).getHttpStatus());
        membershipStatus.set(503);
        assertEquals(503, assertThrows(AgentContractException.class, () -> access.issue(request(), target)).getHttpStatus());
        membershipStatus.set(204); identityStatus.set(500);
        assertEquals(503, assertThrows(AgentContractException.class, () -> access.issue(request(), target)).getHttpStatus());
    }

    /** 并发重放时只能有一个握手获得授权。Exactly one handshake wins concurrent replay attempts. */
    @Test
    void consumesAtomicallyAcrossConcurrentHandshakes() throws Exception {
        var ticket = access.issue(request(), target);
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> attempts = new ArrayList<>();
            for (int index = 0; index < 8; index++) attempts.add(() -> {
                try { access.consume(ticket.ticket(), "https://aiops.example.test", target.path()); return true; }
                catch (AgentContractException exception) { return false; }
            });
            int accepted = 0; for (var result : executor.invokeAll(attempts)) if (result.get()) accepted++;
            assertEquals(1, accepted);
        } finally { executor.shutdownNow(); }
    }

    /** 配置拒绝通配符、恶意路径和读权限提升。Reject wildcard configuration, malicious paths and read-permission escalation. */
    @Test
    void validatesGrantAndOriginConfiguration() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service("*", grants("7", target)));
        assertThrows(IllegalArgumentException.class, () -> service("https://aiops.example.test/path", grants("7", target)));
        assertThrows(IllegalArgumentException.class, () -> service("https://aiops.example.test", grants("7", new TerminalAccessService.Target(1L,"*","pod","app"))));
        assertThrows(IllegalArgumentException.class, () -> service("https://aiops.example.test", grants("7", target).replace("terminal:exec", "read")));
        assertThrows(AgentContractException.class, () -> access.issue(request(), new TerminalAccessService.Target(1L,"team","../other","app")));
    }

    /** 原始Handler不能绕过握手授权执行Pod命令。The raw handler cannot execute pod commands by bypassing handshake authorization. */
    @Test
    void deniesHandlerBypassBeforeCallingKubernetes() throws Exception {
        K8sClientFactory factory = mock(K8sClientFactory.class);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUri()).thenReturn(java.net.URI.create("https://aiops.example.test" + target.path()));
        when(session.getAttributes()).thenReturn(Map.of());
        new K8sTerminalHandler(factory).afterConnectionEstablished(session);
        verify(session).close(CloseStatus.POLICY_VIOLATION); verifyNoInteractions(factory);
    }

    /** 子协议握手必须明确携带单次票据，票据不作为协商结果返回。Subprotocol handshakes require an explicit ticket without negotiating the ticket itself. */
    @Test
    void verifiesHandshakeProtocolsAndExactPath() {
        var interceptor = new TerminalHandshakeInterceptor(access);
        var request = request(); request.setRequestURI(target.path());
        var response = new MockHttpServletResponse();
        assertFalse(interceptor.beforeHandshake(new ServletServerHttpRequest(request), new ServletServerHttpResponse(response), null, new HashMap<>()));
        assertEquals(401, response.getStatus());
        var ticket = access.issue(request(), target);
        request.addHeader("Sec-WebSocket-Protocol", "synapxnet-terminal, ticket." + ticket.ticket());
        var attributes = new HashMap<String, Object>();
        assertTrue(interceptor.beforeHandshake(new ServletServerHttpRequest(request), new ServletServerHttpResponse(new MockHttpServletResponse()), null, attributes));
        assertInstanceOf(TerminalAccessService.AuthorizedTerminal.class, attributes.get(TerminalAccessService.ATTRIBUTE));
        assertEquals(List.of("synapxnet-terminal"), new K8sTerminalHandler(mock(K8sClientFactory.class)).getSubProtocols());
    }

    /** HTTP失败保留真实状态并禁止缓存票据。HTTP failures preserve real status codes and ticket responses disable caching. */
    @Test
    void preservesHttpFailureAndNoStore() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new TerminalTicketController(access)).build();
        mvc.perform(post("/api/k8s/terminal/tickets").contentType("application/json").content(mapper.writeValueAsString(target)))
                .andExpect(status().isForbidden()).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.code").value(403));
        mvc.perform(post("/api/k8s/terminal/tickets").contentType("application/json").content(mapper.writeValueAsString(target))
                .header("Origin","https://aiops.example.test").header("Authorization","Bearer fixture-jwt")
                .header("X-Tenant-Uid","tenant").header("X-Dept-Uid","dept").header("X-Team-Uid","team"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.data.workspaceId").value("project-one"));
    }
}
