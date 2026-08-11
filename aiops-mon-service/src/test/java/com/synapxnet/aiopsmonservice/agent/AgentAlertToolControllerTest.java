package com.synapxnet.aiopsmonservice.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证比赛告警快照的白名单与跨层诊断语义。
 */
class AgentAlertToolControllerTest {

    /** 推荐告警必须同时呈现 GPU 拥塞和 CPU HPA 未触发。 */
    @Test
    void returnsRecommendationCapacityAlert() {
        AgentAlertToolController.AlertEvidence evidence =
                AgentAlertToolController.competitionSandboxEvidence("alert_rec_p99_spike");

        assertEquals("FIRING", evidence.status());
        assertEquals("recommendation-prod", evidence.relatedResource().namespace());
        assertTrue(evidence.description().contains("CPU HPA"));
    }

    /** 量化告警必须表达模型质量退化而非基础服务故障。 */
    @Test
    void returnsQuantitativeQualityAlert() {
        AgentAlertToolController.AlertEvidence evidence =
                AgentAlertToolController.competitionSandboxEvidence("alert_quant_ic_degradation");

        assertEquals("deploy_quant_value_prod", evidence.relatedResource().uid());
        assertTrue(evidence.description().contains("IC"));
        assertTrue(evidence.description().contains("基础服务健康"));
    }

    /** 未列入比赛白名单的告警不得获得虚构证据。 */
    @Test
    void rejectsUnknownSandboxAlert() {
        assertNull(AgentAlertToolController.competitionSandboxEvidence("alert_unknown"));
    }
}
