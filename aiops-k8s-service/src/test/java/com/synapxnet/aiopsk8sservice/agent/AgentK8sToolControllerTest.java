package com.synapxnet.aiopsk8sservice.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证比赛 Workload 快照只能由完整白名单资源坐标命中。
 */
class AgentK8sToolControllerTest {

    /** 推荐 Workload 必须保留初始 6 副本和 GPU 容量诊断。 */
    @Test
    void returnsRecommendationWorkloadSnapshot() {
        AgentK8sToolController.WorkloadEvidence evidence =
                AgentK8sToolController.competitionSandboxEvidence(new AgentK8sToolController.WorkloadArguments(
                        "3", "recommendation-prod", "Deployment", "recommendation-inference", 30));

        assertEquals(6, evidence.desiredReplicas());
        assertEquals(6, evidence.pods().size());
        assertTrue(evidence.warnings().contains("GPU_CAPACITY_SATURATED"));
    }

    /** 量化 Workload 必须表示基础设施就绪且模型质量另行归因。 */
    @Test
    void returnsQuantitativeWorkloadSnapshot() {
        AgentK8sToolController.WorkloadEvidence evidence =
                AgentK8sToolController.competitionSandboxEvidence(new AgentK8sToolController.WorkloadArguments(
                        "3", "quant-prod", "Deployment", "quant-signal-inference", 15));

        assertEquals(3, evidence.readyReplicas());
        assertTrue(evidence.warnings().contains("MODEL_QUALITY_DEGRADED"));
    }

    /** 任一资源坐标不同都不得获得比赛沙盘证据。 */
    @Test
    void rejectsUnknownWorkloadCoordinates() {
        assertNull(AgentK8sToolController.competitionSandboxEvidence(
                new AgentK8sToolController.WorkloadArguments(
                        "3", "recommendation-prod", "Deployment", "unknown", 30)));
        assertNull(AgentK8sToolController.competitionSandboxEvidence(
                new AgentK8sToolController.WorkloadArguments(
                        "4", "recommendation-prod", "Deployment", "recommendation-inference", 30)));
    }
}
