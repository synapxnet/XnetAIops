/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 独立运行保障服务的只读边界。 Read-only boundary for a dedicated operations service.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-15 | Version: 1.0.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.goai.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

public final class OperationsOnlyFilter extends OncePerRequestFilter {
    private final ObjectMapper mapper;

    /** 使用统一 JSON 序列化器返回公开错误。 Uses the shared JSON serializer for public errors. */
    public OperationsOnlyFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** 只放行证据目录和详情 GET/HEAD，其他路径及写方法在进入领域服务前拒绝。 Allows catalog/detail GET and HEAD only; rejects other paths and mutations before domain dispatch. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean route = request.getRequestURI().matches("/api/(mon|svm|k8s)/operations-workspace(?:/evidence)?");
        if (route && Set.of("GET", "HEAD").contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(route ? 405 : 404);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), Map.of("code", response.getStatus(),
                "message", route ? "运行保障仅支持读取。" : "此独立服务仅提供运行保障证据。"));
    }
}
