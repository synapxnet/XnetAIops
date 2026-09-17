/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 注册只读治理与身份组件。 Registers read-only governance and identity components.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-13 | Version: 1.0.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.goai.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class AgentContractAutoConfiguration {
    /** 绑定计划审批内省配置，空凭据拒绝执行。 Binds plan approval introspection and fails closed without credentials. */
    @Bean
    GovernedApprovalVerifier governedApprovalVerifier(
            ObjectMapper mapper,
            @Value("${openxnet.approval.base-url:http://127.0.0.1:8080}") String baseUrl,
            @Value("${openxnet.approval.service-token:}") String serviceToken,
            @Value("${openxnet.approval.connect-timeout-ms:3000}") int connectTimeoutMillis,
            @Value("${openxnet.approval.read-timeout-ms:5000}") int readTimeoutMillis) {
        return new GovernedApprovalVerifier(mapper, baseUrl, serviceToken, connectTimeoutMillis, readTimeoutMillis);
    }

    /** 独立运行保障服务只接受证据读取，禁止访问原领域写接口。 Restricts a dedicated operations service to evidence reads and blocks native write routes. */
    @Bean
    @ConditionalOnProperty(name = "openxnet.operations.read-only-service", havingValue = "true")
    FilterRegistrationBean<OperationsOnlyFilter> operationsOnlyFilter(ObjectMapper mapper) {
        FilterRegistrationBean<OperationsOnlyFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new OperationsOnlyFilter(mapper));
        bean.addUrlPatterns("/*");
        bean.setOrder(-200);
        return bean;
    }

    /** 创建独立委托验证器；缺密钥失败关闭。 Creates the independent delegated verifier; absent keys fail closed. */
    @Bean
    DelegatedTokenVerifier delegatedTokenVerifier(
            @Value("${openxnet.agent.delegation-secret:}") String secret,
            @Value("${openxnet.agent.audience:openxnet-agent-adapter}") String audience) {
        return new DelegatedTokenVerifier(secret, audience);
    }

    /** 仅保护Agent路径，不改变网页登录。 Protects only Agent routes without changing browser authentication. */
    @Bean
    FilterRegistrationBean<AgentRequestContextFilter> agentRequestContextFilter(
            DelegatedTokenVerifier verifier, ObjectMapper mapper) {
        FilterRegistrationBean<AgentRequestContextFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new AgentRequestContextFilter(verifier, mapper));
        bean.addUrlPatterns("/api/agent/v1/*");
        bean.setOrder(-100);
        return bean;
    }

    /** 保留统一结构化错误。 Preserves the compatible structured Agent error envelope. */
    @Bean
    AgentExceptionHandler agentExceptionHandler() {
        return new AgentExceptionHandler();
    }

    /** 注入服务器管理的授权映射，默认不开放资源。 Loads server-managed grants with no default resource access. */
    @Bean
    OperationsReadAccess operationsReadAccess(
            ObjectMapper mapper,
            @Value("${openxnet.operations.organization-access-url:}") String accessUrl,
            @Value("${openxnet.operations.resource-scopes:[]}") String grants,
            @Value("${openxnet.operations.database-time-zone:}") String databaseTimeZone) {
        return new OperationsReadAccess(mapper, accessUrl, grants, databaseTimeZone);
    }
}
