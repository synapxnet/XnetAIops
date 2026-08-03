package com.synapxnet.goai.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * 自动注册 GOAI Agent 契约的鉴权过滤器和结构化异常处理器。
 */
@AutoConfiguration
public class AgentContractAutoConfiguration {

    /**
     * 创建短期委托令牌验证器。
     *
     * @param secret 环境注入的 HMAC 密钥
     * @param audience Adapter 受众
     * @return 失败关闭的令牌验证器
     */
    @Bean
    DelegatedTokenVerifier delegatedTokenVerifier(
            @Value("${openxnet.agent.delegation-secret:}") String secret,
            @Value("${openxnet.agent.audience:openxnet-agent-adapter}") String audience) {
        return new DelegatedTokenVerifier(secret, audience);
    }

    /**
     * 注册只作用于 Agent v1 API 的上下文过滤器。
     *
     * @param verifier 委托令牌验证器
     * @param objectMapper Spring 统一配置的 JSON 序列化器
     * @return Servlet 过滤器注册对象
     */
    @Bean
    FilterRegistrationBean<AgentRequestContextFilter> agentRequestContextFilter(
            DelegatedTokenVerifier verifier,
            ObjectMapper objectMapper) {
        FilterRegistrationBean<AgentRequestContextFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new AgentRequestContextFilter(verifier, objectMapper));
        bean.addUrlPatterns("/api/agent/v1/*");
        bean.setOrder(-100);
        return bean;
    }

    /**
     * 注册公共契约异常处理器。
     *
     * @return 结构化异常处理器
     */
    @Bean
    AgentExceptionHandler agentExceptionHandler() {
        return new AgentExceptionHandler();
    }
}
