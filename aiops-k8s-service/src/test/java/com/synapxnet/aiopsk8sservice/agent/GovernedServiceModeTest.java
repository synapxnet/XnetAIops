/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 读取实例不得初始化治理执行器。 Read-only instances must never initialize the governed executor.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-17 | Version: 1.3.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopsk8sservice.agent;

import com.synapxnet.goai.contract.GovernedApprovalVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GovernedServiceModeTest {
    /** 只在隔离容器中注册执行组件，所有外部审批均由mock替代。 Registers isolated beans with mocked external approval. */
    private ApplicationContextRunner context() {
        return new ApplicationContextRunner()
                .withBean(GovernedApprovalVerifier.class, () -> mock(GovernedApprovalVerifier.class))
                .withUserConfiguration(CompetitionInferenceToolController.class);
    }

    /** 原生reader不能初始化执行器或消费迁移输入。 Native reader never initializes execution or consumes migration input. */
    @Test
    void excludesExecutionFromNativeReader() {
        context().withPropertyValues("openxnet.k8s.ui-reader=true",
                "goai.resource-state-migration-file=must-not-be-opened")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(0, context.getBeansOfType(CompetitionInferenceToolController.class).size());
                });
    }

    /** 运行保障服务只能读取，不能加载治理执行器。 Operations-only services cannot load governed execution. */
    @Test
    void excludesExecutionFromOperationsReader() {
        context().withPropertyValues("openxnet.operations.read-only-service=true",
                "goai.resource-state-migration-file=must-not-be-opened")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(0, context.getBeansOfType(CompetitionInferenceToolController.class).size());
                });
    }

    /** 独立治理实例保留原有执行组件，测试不访问外部服务。 Dedicated governed mode retains execution without external requests in this test. */
    @Test
    void retainsExecutionInGovernedMode() {
        context().run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(CompetitionInferenceToolController.class).size());
        });
    }
}
