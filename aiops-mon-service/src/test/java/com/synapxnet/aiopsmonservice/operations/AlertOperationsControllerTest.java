/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 验证网页错误包络不与Agent合同混用。 Verifies that Web failures preserve their native envelope.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-13 | Version: 1.0.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopsmonservice.operations;

import com.synapxnet.aiopsmonservice.agent.AgentAlertToolController;
import com.synapxnet.goai.contract.AgentContractException;
import com.synapxnet.goai.contract.OperationsReadAccess;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AlertOperationsControllerTest {
    /** 缺配置返回原生503与独立请求标识，且不触达领域读取。 Missing configuration returns a native 503 and request ID without reaching the domain reader. */
    @Test
    void returnsNativeWebErrorWithoutInvokingReader() throws Exception {
        OperationsReadAccess access = mock(OperationsReadAccess.class);
        AgentAlertToolController reader = mock(AgentAlertToolController.class);
        when(access.authorizeWeb(any(), eq("alert"))).thenThrow(
                new AgentContractException(503, "OPERATIONS_SCOPE_NOT_CONFIGURED", "尚未配置资源授权映射"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AlertOperationsController(access, reader)).build();
        mvc.perform(get("/api/mon/operations-workspace"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value("尚未配置资源授权映射"))
                .andExpect(jsonPath("$.success").doesNotExist())
                .andExpect(header().exists("X-Request-Id"));
        verifyNoInteractions(reader);
    }
}
