/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 验证独立读取的身份与拒写边界。 Verifies identity and mutation denial for isolated reads.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-15 | Version: 1.3.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopsk8sservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class K8sUiReaderBoundaryTest {
    /** 拒绝所有写方法、执行别名和升级入口，领域链不得被调用。 Denies mutations, execution aliases and upgrades without entering the domain chain. */
    @ParameterizedTest
    @CsvSource({"POST,/api/k8s/clusters,503", "PUT,/api/k8s/clusters/3,503",
            "PATCH,/api/k8s/clusters/3,503", "DELETE,/api/k8s/clusters/3,503",
            "POST,/api/agent/v1/tools/aiops.k8s.workload.get:invoke,503",
            "GET,/api/agent/v1/tools/aiops.inference.capacity:invoke,503",
            "GET,/ws/terminal/3,503", "GET,/api/k8s/terminal/tickets,503",
            "GET,/api/k8s/clusters/3/helm/apps,503", "GET,/api/k8s/clusters/3/kubeconfig,404",
            "GET,/api/k8s/clusters/3/exec,503", "GET,/api/k8s/clusters/3;exec,503"})
    void rejectsUnsafeRoutesBeforeDispatch(String method, String path, int status) throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new K8sUiReaderFilter(new ObjectMapper(), "").doFilter(new MockHttpServletRequest(method, path), response, chain);
        assertEquals(status, response.getStatus());
        verifyNoInteractions(chain);
    }

    /** 未登录的目录查询不得进入服务。 Denies unauthenticated catalog queries before service dispatch. */
    @Test
    void requiresAuthentication() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new K8sUiReaderFilter(new ObjectMapper(), "").doFilter(new MockHttpServletRequest("GET", "/api/k8s/clusters"), response, chain);
        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain);
    }

    /** 身份服务通过才能读取；撤销权限和身份故障不能回退放行。 Allows reads only after identity success; revoked access and identity failures remain denied. */
    @ParameterizedTest
    @CsvSource({"204,200,true", "401,401,false", "403,403,false", "500,503,false"})
    @SuppressWarnings("unchecked")
    void honorsIdentityDecision(int upstream, int expected, boolean dispatched) throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<Void> identity = mock(HttpResponse.class);
        when(identity.statusCode()).thenReturn(upstream);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(identity);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/k8s/clusters/3/nodes");
        request.addHeader("Authorization", "Bearer test-token");
        request.addHeader("X-Tenant-Uid", "TEN-TEST");
        request.addHeader("X-Dept-Uid", "DEPT-TEST");
        request.addHeader("X-Team-Uid", "TEAM-TEST");
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new K8sUiReaderFilter(new ObjectMapper(), "http://identity/access", client).doFilter(request, response, chain);
        assertEquals(expected, response.getStatus());
        if (dispatched) verify(chain).doFilter(request, response); else verifyNoInteractions(chain);
    }

    /** 即使路径看似读取，也拒绝 WebSocket 升级。 Rejects websocket upgrades even on otherwise readable paths. */
    @Test
    void blocksUpgrades() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/k8s/clusters");
        request.addHeader("Upgrade", "websocket");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        new K8sUiReaderFilter(new ObjectMapper(), "").doFilter(request, response, chain);
        assertEquals(503, response.getStatus());
        verifyNoInteractions(chain);
    }

    /** 数据库写拦截器不能执行下一层调用。 Ensures the SQL write interceptor never invokes downstream execution. */
    @Test
    void deniesDatabaseMutationWithoutProceeding() {
        Invocation invocation = mock(Invocation.class);
        assertThrows(IllegalStateException.class, () -> new K8sUiReaderWriteInterceptor().intercept(invocation));
        verifyNoInteractions(invocation);
    }
}
