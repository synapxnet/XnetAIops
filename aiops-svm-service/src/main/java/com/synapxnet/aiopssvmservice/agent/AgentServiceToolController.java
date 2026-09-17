/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 恢复真实领域只读取证。 Restores read-only evidence from native domain services.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-13 | Version: 1.0.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 * Selectively adapted from SynapXnet competition baseline b260b27.
 */
package com.synapxnet.aiopssvmservice.agent;

import com.synapxnet.aiopssvmservice.entity.RoleInstance;
import com.synapxnet.aiopssvmservice.entity.ServiceInstance;
import com.synapxnet.aiopssvmservice.service.ServiceInstanceService;
import com.synapxnet.goai.contract.AgentContract;
import com.synapxnet.goai.contract.OperationsReadAccess;
import com.synapxnet.goai.contract.AgentContractException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 暴露服务实例健康证据工具，并由后端统一计算健康结论。
 * English: Exposes native service snapshots and derives conservative role health conclusions.
 */
@RestController
public class AgentServiceToolController {

    private static final String TOOL_NAME = "aiops.service.health";
    private final ServiceInstanceService serviceInstanceService;
    private final OperationsReadAccess access;

    /**
     * 创建服务健康工具 Controller。
     *
     * @param serviceInstanceService 服务实例领域服务
     * @param access 资源授权与来源时区配置 / resource authorization and source timezone configuration
     * English: Injects the native service reader, scoped authorization and explicit source timezone.
     */
    public AgentServiceToolController(
            ServiceInstanceService serviceInstanceService,
            OperationsReadAccess access) {
        this.serviceInstanceService = serviceInstanceService;
        this.access = access;
    }

    /**
     * 获取服务状态、角色实例和后端健康结论。
     *
     * @param body 强类型工具请求
     * @param servletRequest 当前 HTTP 请求
     * @return 服务健康证据
     * English: Validates the delegated workspace and returns evidence from the authorized native resource.
     */
    @PostMapping("/api/agent/v1/tools/aiops.service.health:invoke")
    public AgentContract.ToolResponse<ServiceHealthEvidence> invoke(
            @RequestBody AgentContract.ToolRequest<ServiceHealthArguments> body,
            HttpServletRequest servletRequest) {
        long startedNanos = System.nanoTime();
        AgentContract.RequestContext context = AgentContract.requireContext(servletRequest, TOOL_NAME, body);
        ServiceHealthArguments arguments = requireArguments(body.arguments());
        access.requireAgentResource(context.workspaceId(), "service", arguments.serviceUid());
        ServiceHealthEvidence evidence = read(arguments.serviceUid());
        return AgentContract.success(evidence, context, "XnetAIops/svm", evidence.configVersion() == null ? null : String.valueOf(evidence.configVersion()), startedNanos);
    }

    /** 只读取现有服务与角色，不访问演练运行时。 English: Reads the native service and roles without consulting a synthetic runtime. */
    public ServiceHealthEvidence read(String uid) {
        ServiceInstance service = loadService(uid);
        List<RoleInstance> roles = serviceInstanceService.listRoles(service.getId());
        return assemble(service, roles);
    }

    /**
     * 校验服务 UID 和观测窗口。
     *
     * @param arguments 查询参数
     * @return 已校验参数
     * English: Validates the required identifiers and bounded observation window before any lookup.
     */
    private ServiceHealthArguments requireArguments(ServiceHealthArguments arguments) {
        if (arguments == null || arguments.serviceUid() == null || arguments.serviceUid().isBlank()) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "serviceUid 不能为空");
        }
        Integer window = arguments.windowMinutes();
        if (window != null && (window < 1 || window > 1440)) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "windowMinutes 必须在 1 到 1440 之间");
        }
        return arguments;
    }

    /**
     * 通过领域服务读取服务实例并统一映射不存在错误。
     *
     * @param uid 服务 UID
     * @return 服务实例
     * English: Resolves the exact service instance from the native domain service.
     */
    private ServiceInstance loadService(String uid) {
        try {
            ServiceInstance service = serviceInstanceService.getByUid(uid);
            if (service == null) throw new IllegalArgumentException("Service missing");
            return service;
        } catch (IllegalArgumentException exception) {
            throw new AgentContractException(404, "RESOURCE_NOT_FOUND", "服务实例不存在或已删除");
        }
    }

    /**
     * 聚合服务和角色实例，健康算法只在后端定义一次。
     *
     * @param service 服务实例
     * @param roles 角色实例列表
     * @return 服务健康证据
     * English: Builds evidence from native domain records without invented resource associations.
     */
    private ServiceHealthEvidence assemble(
            ServiceInstance service,
            List<RoleInstance> roles) {
        List<RoleHealth> roleHealth = roles.stream().map(this::toRoleHealth).toList();
        long running = roles.stream().filter(role -> "running".equalsIgnoreCase(role.getStatus())).count();
        long failed = roles.stream().filter(role -> isFailed(role.getStatus())).count();
        List<String> reasonCodes = new ArrayList<>();
        HealthConclusion conclusion = conclude(service, roles, failed, reasonCodes);
        reasonCodes.add("WINDOW_UNAVAILABLE_SNAPSHOT_ONLY");
        reasonCodes.add("RECENT_OPERATIONS_UNAVAILABLE");
        return new ServiceHealthEvidence(
                service.getUid(), service.getServiceName(), service.getStatus(), service.getConfigVersion(),
                Boolean.TRUE.equals(service.getNeedRestart()), roles.size(), running, failed,
                roleHealth, List.of(), conclusion, List.copyOf(reasonCodes), toInstant(service.getUpdatedAt()));
    }

    /**
     * 根据服务状态、角色失败数和重启标志计算固定健康枚举。
     *
     * @param service 服务实例
     * @param roles 角色实例
     * @param failed 失败角色数量
     * @param reasonCodes 原因码收集器
     * @return 固定健康结论
     * English: Derives service health from actual status and role records, preserving unknown states.
     */
    private HealthConclusion conclude(
            ServiceInstance service,
            List<RoleInstance> roles,
            long failed,
            List<String> reasonCodes) {
        String status = service.getStatus() == null ? "" : service.getStatus().toLowerCase();
        if (status.equals("failed") || status.equals("error") || status.equals("stopped")) {
            reasonCodes.add("SERVICE_NOT_RUNNING");
            return HealthConclusion.UNHEALTHY;
        }
        if (!status.equals("running") || roles.isEmpty()) {
            reasonCodes.add(roles.isEmpty() ? "ROLE_METRICS_MISSING" : "SERVICE_STATE_UNKNOWN");
            return HealthConclusion.UNKNOWN;
        }
        if (failed > 0 || Boolean.TRUE.equals(service.getNeedRestart())
                || roles.stream().anyMatch(role -> Boolean.TRUE.equals(role.getNeedRestart()))) {
            reasonCodes.add(failed > 0 ? "ROLE_INSTANCE_FAILED" : "CONFIG_RESTART_REQUIRED");
            return HealthConclusion.DEGRADED;
        }
        // 全部角色明确运行才能健康；停止与未知不能被计为正常。 Every role must explicitly run; stopped and unknown states are not healthy.
        if (roles.stream().anyMatch(role -> "stopped".equalsIgnoreCase(role.getStatus()))) {
            reasonCodes.add("ROLE_INSTANCE_STOPPED");
            return HealthConclusion.DEGRADED;
        }
        if (roles.stream().anyMatch(role -> !"running".equalsIgnoreCase(role.getStatus()))) {
            reasonCodes.add("ROLE_STATE_UNKNOWN");
            return HealthConclusion.UNKNOWN;
        }
        return HealthConclusion.HEALTHY;
    }

    /**
     * 判断角色状态是否属于明确失败状态。
     *
     * @param status 角色状态
     * @return 是否失败
     * English: Identifies only explicit failed or error role states; unknown states remain distinct.
     */
    private boolean isFailed(String status) {
        return status != null && (status.equalsIgnoreCase("failed") || status.equalsIgnoreCase("error"));
    }

    /**
     * 将角色 Entity 转换为健康 DTO。
     *
     * @param role 角色实例
     * @return 角色健康证据
     * English: Maps one native role instance into the read-only health record.
     */
    private RoleHealth toRoleHealth(RoleInstance role) {
        return new RoleHealth(
                role.getUid(), role.getRoleName(), role.getHostname(), role.getStatus(),
                Boolean.TRUE.equals(role.getNeedRestart()), toInstant(role.getUpdatedAt()));
    }

    /**
     * 仅按显式配置的数据库时区转换时间，未配置保留未知。
     *
     * @param value 数据库时间
     * @return UTC Instant
     * English: Converts source database timestamps using an explicit timezone and preserves unknown configuration.
     */
    private Instant toInstant(java.time.LocalDateTime value) {
        return access.databaseInstant(value);
    }

    /** 表示服务健康查询参数。 English: Defines a typed, read-only native evidence contract and its source metadata. */
    public record ServiceHealthArguments(String serviceUid, Integer windowMinutes) {
    }

    /** 表示单个角色实例的健康状态。 English: Defines a typed, read-only native evidence contract and its source metadata. */
    public record RoleHealth(
            String roleUid,
            String roleName,
            String hostname,
            String status,
            boolean needRestart,
            Instant observedAt) {
    }

    /** 表示最近的受控服务操作；当前数据源不可用时返回空列表和原因码。 English: Defines a typed, read-only native evidence contract and its source metadata. */
    public record RecentOperation(String operationUid, String type, String status, Instant completedAt) {
    }

    /** 表示固定的后端健康结论。 English: Defines a typed, read-only native evidence contract and its source metadata. */
    public enum HealthConclusion {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        UNKNOWN
    }

    /** 表示可跨平台引用的服务健康证据。 English: Defines a typed, read-only native evidence contract and its source metadata. */
    public record ServiceHealthEvidence(
            String serviceUid,
            String serviceName,
            String status,
            Integer configVersion,
            boolean needRestart,
            int roleCount,
            long runningRoleCount,
            long failedRoleCount,
            List<RoleHealth> roles,
            List<RecentOperation> recentOperations,
            HealthConclusion conclusion,
            List<String> reasonCodes,
            Instant observedAt) {
    }
}
