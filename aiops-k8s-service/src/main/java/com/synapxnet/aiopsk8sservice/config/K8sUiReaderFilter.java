/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 原生界面读取、组织验证和执行隔离。 Native UI reads, organization verification and execution isolation.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-15 | Version: 1.3.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopsk8sservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

public final class K8sUiReaderFilter extends OncePerRequestFilter {
    private final ObjectMapper mapper;
    private final URI accessUri;
    private final HttpClient client;

    /** 固定组织验证地址与连接超时；不接受客户端认证地址。 Fixes the identity endpoint and connection timeout without client-selected authentication URLs. */
    public K8sUiReaderFilter(ObjectMapper mapper, String accessUrl) {
        this(mapper, accessUrl, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    }

    /** 注入传输便于验证失败关闭的行为。 Injects transport to verify fail-closed behavior. */
    K8sUiReaderFilter(ObjectMapper mapper, String accessUrl, HttpClient client) {
        this.mapper = mapper;
        this.client = client;
        this.accessUri = accessUrl == null || accessUrl.isBlank() ? null : URI.create(accessUrl);
        if (accessUri != null && (!Set.of("http", "https").contains(accessUri.getScheme())
                || accessUri.getHost() == null || accessUri.getUserInfo() != null
                || accessUri.getQuery() != null || accessUri.getFragment() != null)) {
            throw new IllegalArgumentException("Invalid reader identity endpoint");
        }
    }

    /** 在路由和身份校验通过后才执行读取，所有执行入口显式不可用。 Dispatches reads only after route and identity checks; execution endpoints remain explicitly unavailable. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.matches("/api/k8s/clusters/[0-9]+/kubeconfig/?")) {
            reject(response, 404, "RESOURCE_NOT_FOUND", "资源不存在。");
            return;
        }
        if (!Set.of("GET", "HEAD").contains(request.getMethod()) || !path.startsWith("/api/k8s/")
                || path.contains("%") || path.contains(";") || path.contains("..") || path.contains("//")
                || path.matches(".*/(terminal|exec|execute|attach|proxy|portforward|sync|restart|scale|rollback)(/.*)?")
                || path.matches(".*/helm/apps(?:/.*)?") || request.getHeader("Upgrade") != null) {
            reject(response, 503, "K8S_EXECUTION_RECOVERY_REQUIRED", "执行服务恢复中，当前服务仅支持界面读取。");
            return;
        }
        int identity = verifyIdentity(request);
        if (identity != 204) {
            reject(response, identity, identity == 401 ? "UNAUTHENTICATED" : "READER_ACCESS_UNAVAILABLE",
                    identity == 401 ? "请先登录。" : "当前组织读取校验未通过。");
            return;
        }
        response.setHeader("X-OpenXnet-Service-Mode", "ui-reader");
        chain.doFilter(request, response);
    }

    /** 将原 JWT 与组织标识交给已有身份服务，错误时拒绝读取。 Validates the original JWT and organization through the existing identity service and denies errors. */
    private int verifyIdentity(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ") || token.length() > 8192) return 401;
        if (accessUri == null) return 503;
        HttpRequest.Builder verification = HttpRequest.newBuilder(accessUri).timeout(Duration.ofSeconds(5))
                .header("Authorization", token).GET();
        for (String name : List.of("X-Tenant-Uid", "X-Dept-Uid", "X-Team-Uid")) {
            String value = request.getHeader(name);
            if (value == null || !value.matches("[A-Za-z0-9._:-]{1,128}")) return 403;
            verification.header(name, value);
        }
        try {
            int status = client.send(verification.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
            return Set.of(204, 401, 403).contains(status) ? status : 503;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return 503;
        } catch (Exception exception) {
            return 503;
        }
    }

    /** 返回无敏感内容的结构化错误。 Returns a structured error without sensitive content. */
    private void reject(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), Map.of("code", status, "error", error, "message", message));
    }
}
