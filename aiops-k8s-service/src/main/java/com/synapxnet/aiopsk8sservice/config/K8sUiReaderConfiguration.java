/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 独立界面读取边界。 Isolated UI reader boundary.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-15 | Version: 1.3.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopsk8sservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "openxnet.k8s.ui-reader", havingValue = "true")
public class K8sUiReaderConfiguration {
    /** 在领域控制器之前注册只读与身份边界。 Registers read and identity boundaries before domain controllers. */
    @Bean
    FilterRegistrationBean<K8sUiReaderFilter> k8sUiReaderFilter(ObjectMapper mapper,
            @Value("${openxnet.operations.organization-access-url:}") String accessUrl) {
        FilterRegistrationBean<K8sUiReaderFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new K8sUiReaderFilter(mapper, accessUrl));
        bean.addUrlPatterns("/*");
        bean.setOrder(-300);
        return bean;
    }

    /** 将所有 MyBatis 数据库写入在执行前拒绝。 Rejects every MyBatis database mutation before execution. */
    @Bean
    K8sUiReaderWriteInterceptor k8sUiReaderWriteInterceptor() {
        return new K8sUiReaderWriteInterceptor();
    }
}
