/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 验证服务证据不合成健康结论。 Verifies service evidence does not synthesize healthy results.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-13 | Version: 1.0.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopssvmservice.agent;

import com.synapxnet.aiopssvmservice.service.ServiceInstanceService;
import com.synapxnet.aiopssvmservice.entity.ServiceInstance;
import com.synapxnet.aiopssvmservice.entity.RoleInstance;
import java.util.List;
import com.synapxnet.goai.contract.AgentContractException;
import com.synapxnet.goai.contract.OperationsReadAccess;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentServiceToolControllerTest {
    /** 已停止与未知角色不能被误判健康；快照不伪装窗口评测。 Stopped and unknown roles cannot appear healthy, and snapshots never pretend to be window evaluations. */
    @Test
    void distinguishesStoppedUnknownAndRunningRoles() {
        ServiceInstanceService service = mock(ServiceInstanceService.class);
        ServiceInstance instance = new ServiceInstance();
        instance.setId(1L);
        instance.setUid("service-one");
        instance.setStatus("running");
        RoleInstance role = new RoleInstance();
        when(service.getByUid("service-one")).thenReturn(instance);
        when(service.listRoles(1L)).thenReturn(List.of(role));
        AgentServiceToolController reader = new AgentServiceToolController(service, mock(OperationsReadAccess.class));
        role.setStatus("stopped");
        assertEquals(AgentServiceToolController.HealthConclusion.DEGRADED, reader.read("service-one").conclusion());
        role.setStatus(null);
        assertEquals(AgentServiceToolController.HealthConclusion.UNKNOWN, reader.read("service-one").conclusion());
        role.setStatus("running");
        AgentServiceToolController.ServiceHealthEvidence result = reader.read("service-one");
        assertEquals(AgentServiceToolController.HealthConclusion.HEALTHY, result.conclusion());
        assertTrue(result.reasonCodes().contains("WINDOW_UNAVAILABLE_SNAPSHOT_ONLY"));
        assertNull(result.observedAt());
    }

    /** 旧量化标识不存在时保持失败而非返回健康。 A missing historical quantitative service must fail instead of appearing healthy. */
    @Test
    void doesNotSynthesizeQuantitativeServiceHealth() {
        ServiceInstanceService service = mock(ServiceInstanceService.class);
        when(service.getByUid("service_quant_signal")).thenThrow(new IllegalArgumentException("missing"));
        AgentServiceToolController reader = new AgentServiceToolController(service, mock(OperationsReadAccess.class));
        assertEquals("RESOURCE_NOT_FOUND", assertThrows(AgentContractException.class,
                () -> reader.read("service_quant_signal")).getCode());
    }
}
