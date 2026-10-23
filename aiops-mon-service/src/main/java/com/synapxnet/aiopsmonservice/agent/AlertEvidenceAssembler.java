/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 恢复真实领域只读取证。 Restores read-only evidence from native domain services.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-13 | Version: 1.0.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 * Selectively adapted from SynapXnet competition baseline b260b27.
 */
package com.synapxnet.aiopsmonservice.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapxnet.aiopsmonservice.entity.AlertHistory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import com.synapxnet.goai.contract.OperationsReadAccess;

/**
 * 将 MON 告警记录转换为稳定、可关联且不伪造字段的 Agent 证据。
 * English: Converts native alert records into bounded evidence without inventing missing associations.
 */
@Component
public class AlertEvidenceAssembler {

    private final ObjectMapper objectMapper;
    private final OperationsReadAccess access;

    /**
     * 创建告警证据组装器。
     *
     * @param objectMapper Jackson 结构化 JSON 解析器
     * English: Injects structured JSON parsing and explicit database timezone conversion.
     */
    public AlertEvidenceAssembler(ObjectMapper objectMapper, OperationsReadAccess access) {
        this.objectMapper = objectMapper;
        this.access = access;
    }

    /**
     * 解析可选结构化 alert_info 并保留旧纯文本兼容行为。
     *
     * @param alert MON 告警领域记录
     * @return 不包含凭据的告警证据
     * English: Builds evidence from native domain records without invented resource associations.
     */
    public AgentAlertToolController.AlertEvidence assemble(AlertHistory alert) {
        JsonNode root = readStructuredInfo(alert.getAlertInfo());
        String description = text(root, "description", bounded(alert.getAlertInfo()));
        AgentAlertToolController.RelatedResource related = relatedResource(root.path("relatedResource"));
        return new AgentAlertToolController.AlertEvidence(
                alert.getUid(), alert.getAlertName(), alert.getAlertLevel(), alert.getStatus(),
                alert.getClusterId() == null ? null : alert.getClusterId().toString(), alert.getHostname(),
                description, bounded(alert.getAlertAdvice()), toInstant(alert.getTriggeredAt()),
                toInstant(alert.getResolvedAt()), related,
                null);
    }

    /**
     * 使用 Jackson 读取 JSON；旧纯文本或损坏 JSON 返回 MissingNode。
     *
     * @param value 数据库 alert_info
     * @return JSON 根节点或 MissingNode
     * English: Parses optional structured alert metadata and preserves legacy text compatibility.
     */
    private JsonNode readStructuredInfo(String value) {
        if (value == null || value.length() > 16_384 || value.isBlank() || !value.stripLeading().startsWith("{")) {
            return objectMapper.getNodeFactory().missingNode();
        }
        try {
            JsonNode parsed = objectMapper.readTree(value);
            return parsed != null && parsed.isObject()
                    ? parsed : objectMapper.getNodeFactory().missingNode();
        } catch (Exception ignored) {
            return objectMapper.getNodeFactory().missingNode();
        }
    }

    /**
     * 只在类型、UID、命名空间和名称完整时建立关联，缺失时不拼造资源。
     *
     * @param node relatedResource JSON 节点
     * @return 受控资源引用或 null
     * English: Creates a related resource only when every required identity field is present.
     */
    private AgentAlertToolController.RelatedResource relatedResource(JsonNode node) {
        String type = text(node, "type", null);
        String uid = text(node, "uid", null);
        String namespace = text(node, "namespace", null);
        String name = text(node, "name", null);
        if (type == null || uid == null || namespace == null || name == null) {
            return null;
        }
        return new AgentAlertToolController.RelatedResource(type, uid, namespace, name);
    }

    /**
     * 读取有界文本字段，空白或超长值回退默认值。
     *
     * @param node JSON 父节点
     * @param field 字段名
     * @param fallback 缺失回退值
     * @return 规范文本或回退值
     * English: Reads a bounded text field from structured source metadata.
     */
    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        if (!value.isTextual()) {
            return fallback;
        }
        String text = value.textValue().trim();
        return text.isEmpty() || text.length() > 2000 ? fallback : text;
    }

    /** 截断外部告警说明，避免返回无界源文本。 Bounds external alert descriptions to avoid returning unbounded source text. */
    private String bounded(String value) {
        return value == null || value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    /**
     * 仅按显式配置的数据库时区转换时间，未配置保留未知。
     *
     * @param value 数据库本地时间
     * @return UTC Instant，空值保持 null
     * English: Converts source database timestamps using an explicit timezone and preserves unknown configuration.
     */
    private Instant toInstant(java.time.LocalDateTime value) {
        return access.databaseInstant(value);
    }
}
