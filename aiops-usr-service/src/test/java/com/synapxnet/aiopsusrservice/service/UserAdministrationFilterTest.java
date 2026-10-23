/* Copyright (C) 2026 Synapxnet. All rights reserved.
 * Synapxnet Proprietary and Confidential. No copying, distribution or use without authorization.
 * 管理权限回归 / Administration permission regression.
 * Author: maoyo | Department: 研发部 | Date: 2026-10-23 | Version: 1.3.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopsusrservice.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapxnet.aiopsusrservice.security.UserAdministrationFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserAdministrationFilterTest {
    /** 不带令牌的管理请求必须在服务层前阻断。 Block unauthenticated administration before controllers. */
    @Test void blocksAnonymousAdministration() throws Exception {
        var auth = mock(AuthService.class); var chain = mock(FilterChain.class);
        var response = call(auth, chain, "POST", "/api/usr/users", false);
        assertEquals(401, response.getStatus()); verifyNoInteractions(auth, chain);
    }
    /** 普通身份不能分配角色或读取全局账号。 Ordinary identities cannot assign roles or enumerate global users. */
    @Test void blocksNonAdministratorAndAcceptsAdmin() throws Exception {
        var auth = mock(AuthService.class); var chain = mock(FilterChain.class);
        when(auth.getUserInfo("valid")).thenReturn(Map.of("userId", 2L, "userType", "user", "roles", List.of("USER")));
        assertEquals(403, call(auth, chain, "POST", "/api/usr/user-roles", true).getStatus());
        verifyNoInteractions(chain);
        when(auth.getUserInfo("valid")).thenReturn(Map.of("userId", 2L, "userType", "user", "roles", List.of("ADMIN")));
        assertEquals(200, call(auth, chain, "GET", "/api/usr/roles", true).getStatus());
        verify(chain).doFilter(any(), any());
    }
    /** 普通用户只允许修改自己的密码。 A user may change only their own password. */
    @Test void permitsOnlyOwnPassword() throws Exception {
        var auth = mock(AuthService.class); var chain = mock(FilterChain.class);
        when(auth.getUserInfo("valid")).thenReturn(Map.of("userId", 2L, "userType", "user", "roles", List.of()));
        assertEquals(403, call(auth, chain, "POST", "/api/usr/users/3/change-password", true).getStatus());
        assertEquals(200, call(auth, chain, "POST", "/api/usr/users/2/change-password", true).getStatus());
        verify(chain).doFilter(any(), any());
    }
    /** 已退出或停用身份必须返回401。 Logged-out or disabled identities must return 401. */
    @Test void rejectsInvalidAndAllowsSmsEndpoint() throws Exception {
        var auth = mock(AuthService.class); var chain = mock(FilterChain.class);
        when(auth.getUserInfo("valid")).thenThrow(new IllegalArgumentException("Invalid or expired token"));
        assertEquals(401, call(auth, chain, "GET", "/api/usr/users", true).getStatus());
        assertEquals(200, call(auth, chain, "POST", "/api/usr/sms-code", false).getStatus());
        verify(chain).doFilter(any(), any());
    }
    /** 构造本地过滤链调用，不发出网络请求。 Invoke a local filter chain without network requests. */
    private MockHttpServletResponse call(AuthService auth, FilterChain chain, String method, String path, boolean authenticated) throws Exception {
        var request = new MockHttpServletRequest(method, path); var response = new MockHttpServletResponse();
        if (authenticated) request.addHeader("Authorization", "Bearer valid");
        new UserAdministrationFilter(auth, new ObjectMapper()).doFilter(request, response, chain);
        return response;
    }
}
