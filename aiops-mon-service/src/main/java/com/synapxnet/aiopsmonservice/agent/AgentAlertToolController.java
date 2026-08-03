package com.synapxnet.aiopsmonservice.agent;

import com.synapxnet.aiopsmonservice.entity.AlertHistory;
import com.synapxnet.aiopsmonservice.service.AlertHistoryService;
import com.synapxnet.goai.contract.AgentContract;
import com.synapxnet.goai.contract.AgentContractException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 暴露只读告警证据工具，同时保持原 MON 页面接口不变。
 */
@RestController
public class AgentAlertToolController {

    private static final String TOOL_NAME = "aiops.alert.get";
    private final AlertHistoryService alertHistoryService;
    private final AlertEvidenceAssembler evidenceAssembler;

    /**
     * 创建告警证据工具 Controller。
     *
     * @param alertHistoryService 告警领域服务
     * @param evidenceAssembler 结构化证据组装器
     */
    public AgentAlertToolController(
            AlertHistoryService alertHistoryService,
            AlertEvidenceAssembler evidenceAssembler) {
        this.alertHistoryService = alertHistoryService;
        this.evidenceAssembler = evidenceAssembler;
    }

    /**
     * 按数字 ID 或稳定 UID 获取告警事实，并返回带 Evidence ID 的公共响应。
     *
     * @param body 强类型工具请求
     * @param servletRequest 当前 HTTP 请求
     * @return 告警结构化证据
     */
    @PostMapping("/api/agent/v1/tools/aiops.alert.get:invoke")
    public AgentContract.ToolResponse<AlertEvidence> invoke(
            @RequestBody AgentContract.ToolRequest<AlertGetArguments> body,
            HttpServletRequest servletRequest) {
        long startedNanos = System.nanoTime();
        AgentContract.RequestContext context = AgentContract.requireContext(servletRequest, TOOL_NAME, body);
        AlertGetArguments arguments = requireArguments(body.arguments());
        AlertHistory alert = loadAlert(arguments);
        AlertEvidence evidence = evidenceAssembler.assemble(alert);
        String sourceVersion = alert.getId() + ":" + alert.getTriggeredAt() + ":" + alert.getResolvedAt();
        return AgentContract.success(evidence, context, "XnetAIops/mon", sourceVersion, startedNanos);
    }

    /**
     * 校验 alertId 和 alertUid 必须且只能提供一个。
     *
     * @param arguments 告警查询参数
     * @return 已校验参数
     */
    private AlertGetArguments requireArguments(AlertGetArguments arguments) {
        boolean hasId = arguments != null && arguments.alertId() != null && !arguments.alertId().isBlank();
        boolean hasUid = arguments != null && arguments.alertUid() != null && !arguments.alertUid().isBlank();
        if (hasId == hasUid) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "alertId 和 alertUid 必须且只能提供一个");
        }
        return arguments;
    }

    /**
     * 通过领域 Service 直接定位告警，不在 Controller 中遍历列表。
     *
     * @param arguments 已校验参数
     * @return 告警领域记录
     */
    private AlertHistory loadAlert(AlertGetArguments arguments) {
        try {
            if (arguments.alertUid() != null && !arguments.alertUid().isBlank()) {
                return alertHistoryService.getByUid(arguments.alertUid());
            }
            return alertHistoryService.getById(Long.parseLong(arguments.alertId()));
        } catch (NumberFormatException exception) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "alertId 必须是有效数字字符串");
        } catch (IllegalArgumentException exception) {
            throw new AgentContractException(404, "RESOURCE_NOT_FOUND", "告警不存在或已删除");
        }
    }

    /** 表示告警工具的互斥查询参数。 */
    public record AlertGetArguments(String alertId, String alertUid) {
    }

    /** 表示告警关联的受控资源引用。 */
    public record RelatedResource(String type, String uid, String namespace, String name) {
    }

    /** 表示可跨平台引用的告警证据。 */
    public record AlertEvidence(
            String alertUid,
            String alertName,
            String level,
            String status,
            String clusterId,
            String hostname,
            String description,
            String advice,
            Instant triggeredAt,
            Instant resolvedAt,
            RelatedResource relatedResource,
            String sourceRecordVersion) {
    }
}
