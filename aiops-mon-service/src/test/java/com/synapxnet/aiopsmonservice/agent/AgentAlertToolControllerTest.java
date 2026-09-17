/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 验证告警证据不回退到演练。 Verifies alert evidence never silently falls back to rehearsal data.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-13 | Version: 1.0.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopsmonservice.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapxnet.aiopsmonservice.service.AlertHistoryService;
import com.synapxnet.goai.contract.AgentContractException;
import com.synapxnet.goai.contract.OperationsReadAccess;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentAlertToolControllerTest {
    /** 即使是旧场景UID，真实告警缺失也保持404。 Even historical scenario UIDs remain missing when no native alert exists. */
    @Test
    void preservesMissingAlertInsteadOfReturningScenario() {
        AlertHistoryService service = mock(AlertHistoryService.class);
        when(service.getByUid("alert_rec_p99_spike")).thenThrow(new IllegalArgumentException("missing"));
        AgentAlertToolController reader = new AgentAlertToolController(service,
                new AlertEvidenceAssembler(new ObjectMapper(), mock(OperationsReadAccess.class)), mock(OperationsReadAccess.class));
        assertEquals("RESOURCE_NOT_FOUND", assertThrows(AgentContractException.class,
                () -> reader.read("alert_rec_p99_spike")).getCode());
    }
}
