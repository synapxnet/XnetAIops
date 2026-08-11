package com.synapxnet.aiopsk8sservice.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证推荐推理完整恢复计划与独立恢复谓词保持一致。
 */
class CompetitionInferenceToolControllerTest {

    /** 完成容量、流量、弹性和动态形状优化收敛后必须判定恢复。 */
    @Test
    void acceptsFinalPlannedState() {
        assertTrue(CompetitionInferenceToolController.hasRecoveredInferenceState(
                4, 12, 64, 100, true, true, "DYNAMIC_SHAPE_OPTIMIZED"));
    }

    /** 紧急调优阶段的批大小尚未恢复时不得提前关闭事件。 */
    @Test
    void rejectsEmergencyBatchSize() {
        assertFalse(CompetitionInferenceToolController.hasRecoveredInferenceState(
                4, 20, 16, 100, true, true, "DYNAMIC_SHAPE_OPTIMIZED"));
    }

    /** 未完成容量收敛时不得仅凭指标改善关闭事件。 */
    @Test
    void rejectsUnconvergedCapacity() {
        assertFalse(CompetitionInferenceToolController.hasRecoveredInferenceState(
                4, 12, 64, 100, true, false, "DYNAMIC_SHAPE_OPTIMIZED"));
    }
}
