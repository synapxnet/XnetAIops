package com.synapxnet.aiopssvmservice.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证比赛服务健康快照的白名单和场景语义。
 */
class AgentServiceToolControllerTest {

    /** 推荐场景必须表示基础服务运行但 GPU 队列退化。 */
    @Test
    void returnsDegradedRecommendationSandboxHealth() {
        AgentServiceToolController.ServiceHealthEvidence evidence =
                AgentServiceToolController.competitionSandboxEvidence("service_rec_inference", 30);

        assertEquals(AgentServiceToolController.HealthConclusion.DEGRADED, evidence.conclusion());
        assertTrue(evidence.reasonCodes().contains("GPU_QUEUE_SATURATED"));
        assertTrue(evidence.reasonCodes().contains("COMPETITION_SANDBOX_SNAPSHOT"));
        assertTrue(evidence.reasonCodes().contains("WINDOW_30M"));
    }

    /** 量化场景必须表示基础设施健康，避免把模型退化误诊为运维故障。 */
    @Test
    void returnsHealthyQuantitativeSandboxHealth() {
        AgentServiceToolController.ServiceHealthEvidence evidence =
                AgentServiceToolController.competitionSandboxEvidence("service_quant_signal", 15);

        assertEquals(AgentServiceToolController.HealthConclusion.HEALTHY, evidence.conclusion());
        assertTrue(evidence.reasonCodes().contains("MODEL_QUALITY_REQUIRES_ATTRIBUTION"));
    }

    /** 未列入比赛白名单的服务不得获得虚构健康证据。 */
    @Test
    void rejectsUnknownSandboxService() {
        assertNull(AgentServiceToolController.competitionSandboxEvidence("service_unknown", 30));
    }
}
