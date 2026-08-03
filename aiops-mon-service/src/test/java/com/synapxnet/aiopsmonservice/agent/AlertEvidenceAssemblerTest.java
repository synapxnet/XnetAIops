package com.synapxnet.aiopsmonservice.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapxnet.aiopsmonservice.entity.AlertHistory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 验证告警结构化字段解析和旧纯文本回退。
 */
class AlertEvidenceAssemblerTest {

    private final AlertEvidenceAssembler assembler = new AlertEvidenceAssembler(new ObjectMapper());

    /** 结构化 Fixture 必须关联 risk-inference 且保留可读事实。 */
    @Test
    void assemblesStructuredGoaiEvidence() {
        AlertHistory alert = alert("""
                {"description":"当前错误率 18%，基线 0.8%，P95 2600ms",\
                "relatedResource":{"type":"K8S_DEPLOYMENT","uid":"risk-inference",\
                "namespace":"risk-prod","name":"risk-inference"}}
                """);

        AgentAlertToolController.AlertEvidence evidence = assembler.assemble(alert);

        assertEquals("当前错误率 18%，基线 0.8%，P95 2600ms", evidence.description());
        assertNotNull(evidence.relatedResource());
        assertEquals("risk-inference", evidence.relatedResource().name());
        assertEquals("risk-prod", evidence.relatedResource().namespace());
    }

    /** 旧纯文本不应被丢弃，也不能拼造关联资源。 */
    @Test
    void preservesLegacyTextWithoutInventingResource() {
        AlertHistory alert = alert("普通告警文本");

        AgentAlertToolController.AlertEvidence evidence = assembler.assemble(alert);

        assertEquals("普通告警文本", evidence.description());
        assertNull(evidence.relatedResource());
    }

    /** 创建最小完整告警 Entity；输入 alertInfo，返回测试记录。 */
    private AlertHistory alert(String alertInfo) {
        AlertHistory alert = new AlertHistory();
        alert.setId(1L);
        alert.setUid("alert_risk_error_rate");
        alert.setClusterId(1L);
        alert.setAlertName("模型推理错误率异常");
        alert.setAlertLevel("critical");
        alert.setStatus("open");
        alert.setHostname("risk-inference");
        alert.setAlertInfo(alertInfo);
        alert.setAlertAdvice("核对模型输入契约与特征 Schema");
        alert.setTriggeredAt(LocalDateTime.of(2026, 8, 3, 2, 0));
        return alert;
    }
}
