/* Copyright (C) 2026 Synapxnet. All rights reserved.
 * Synapxnet Proprietary and Confidential. No copying, distribution or use without authorization.
 * 用户管理鉴权边界 / User-administration authorization boundary.
 * Author: maoyo | Department: 研发部 | Date: 2026-10-23 | Version: 1.3.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopsusrservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapxnet.aiopsusrservice.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/** 管理接口不能依赖前端菜单隐藏。 Administration endpoints cannot rely on hidden frontend menus. */
@Component
public class UserAdministrationFilter extends OncePerRequestFilter {
    private static final Set<String> SELF_AUTHENTICATING = Set.of(
            "/api/usr/login", "/api/usr/sms-code", "/api/usr/logout", "/api/usr/user/info",
            "/api/usr/auth/codes", "/api/usr/organization-tree", "/api/usr/organization-access", "/api/usr/menu/all");
    private final AuthService auth;
    private final ObjectMapper json;

    /** 复用真实令牌、停用状态及退出黑名单校验。 Reuse token, active-account and logout checks. */
    public UserAdministrationFilter(AuthService auth, ObjectMapper json) { this.auth = auth; this.json = json; }

    /** 登录及自认证接口保留自己的精确策略。 Preserve the explicit policies of login and self-authenticating routes. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/usr/") || SELF_AUTHENTICATING.contains(uri);
    }

    /** 用户与角色全局管理仅管理员可用，普通账号只可修改自己的密码。 Restrict global administration while permitting self password changes. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ") || header.substring(7).isBlank()) {
            reject(response, 401, "请先登录"); return;
        }
        Map<String, Object> user;
        try { user = auth.getUserInfo(header.substring(7).trim()); }
        catch (IllegalArgumentException invalid) { reject(response, 401, "登录已失效，请重新登录"); return; }
        boolean administrator = "admin".equalsIgnoreCase(String.valueOf(user.get("userType")))
                || user.get("roles") instanceof Collection<?> roles && roles.stream().anyMatch(role -> "ADMIN".equalsIgnoreCase(String.valueOf(role)));
        String ownPasswordPath = "/api/usr/users/" + user.get("userId") + "/change-password";
        boolean ownPassword = "POST".equals(request.getMethod()) && ownPasswordPath.equals(request.getRequestURI());
        if (!administrator && !ownPassword) { reject(response, 403, "当前账号没有用户与角色管理权限"); return; }
        chain.doFilter(request, response);
    }

    /** 输出稳定错误包络，不暴露认证内部细节。 Return a stable envelope without authentication internals. */
    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getOutputStream(), Map.of("code", status, "message", message));
    }
}
