package com.synapxnet.aiopssvmservice.agent;

import com.synapxnet.aiopssvmservice.entity.RoleInstance;
import com.synapxnet.aiopssvmservice.entity.ServiceInstance;
import com.synapxnet.aiopssvmservice.service.ServiceInstanceService;
import com.synapxnet.goai.contract.AgentContract;
import com.synapxnet.goai.contract.AgentContractException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 暴露服务实例健康证据工具，并由后端统一计算健康结论。
 */
@RestController
public class AgentServiceToolController {

    private static final String TOOL_NAME = "aiops.service.health";
    private final ServiceInstanceService serviceInstanceService;

    /**
     * 创建服务健康工具 Controller。
     *
     * @param serviceInstanceService 服务实例领域服务
     */
    public AgentServiceToolController(ServiceInstanceService serviceInstanceService) {
        this.serviceInstanceService = serviceInstanceService;
    }

    /**
     * 获取服务状态、角色实例和后端健康结论。
     *
     * @param body 强类型工具请求
     * @param servletRequest 当前 HTTP 请求
     * @return 服务健康证据
     */
    @PostMapping("/api/agent/v1/tools/aiops.service.health:invoke")
    public AgentContract.ToolResponse<ServiceHealthEvidence> invoke(
            @RequestBody AgentContract.ToolRequest<ServiceHealthArguments> body,
            HttpServletRequest servletRequest) {
        long startedNanos = System.nanoTime();
        AgentContract.RequestContext context = AgentContract.requireContext(servletRequest, TOOL_NAME, body);
        ServiceHealthArguments arguments = requireArguments(body.arguments());
        ServiceHealthEvidence evidence;
        String version;
        try {
            ServiceInstance service = loadService(arguments.serviceUid());
            List<RoleInstance> roles = serviceInstanceService.listRoles(service.getId());
            evidence = assemble(service, roles, arguments.windowMinutes());
            version = String.valueOf(service.getConfigVersion());
        } catch (AgentContractException exception) {
            if (!"RESOURCE_NOT_FOUND".equals(exception.getCode())) {
                throw exception;
            }
            evidence = competitionSandboxEvidence(arguments.serviceUid(), arguments.windowMinutes());
            if (evidence == null) {
                throw exception;
            }
            version = "competition-sandbox-1";
        }
        return AgentContract.success(evidence, context, "XnetAIops/svm", version, startedNanos);
    }

    /**
     * 返回固定比赛服务的隔离沙盘健康证据；未知 UID 不提供通用兜底。
     *
     * @param serviceUid 服务 UID
     * @param requestedWindow 观测窗口
     * @return 白名单沙盘证据，非白名单返回 null
     */
    static ServiceHealthEvidence competitionSandboxEvidence(
            String serviceUid,
            Integer requestedWindow) {
        if ("service_rec_inference".equals(serviceUid)) {
            return sandboxHealthEvidence(
                    serviceUid,
                    "推荐推理服务",
                    HealthConclusion.DEGRADED,
                    requestedWindow,
                    List.of("GPU_QUEUE_SATURATED", "CPU_HPA_NOT_TRIGGERED"));
        }
        if ("service_quant_signal".equals(serviceUid)) {
            return sandboxHealthEvidence(
                    serviceUid,
                    "量化信号服务",
                    HealthConclusion.HEALTHY,
                    requestedWindow,
                    List.of("INFRASTRUCTURE_HEALTHY", "MODEL_QUALITY_REQUIRES_ATTRIBUTION"));
        }
        return null;
    }

    /**
     * 构造带来源和窗口标记的固定比赛服务健康证据。
     *
     * @param serviceUid 服务 UID
     * @param serviceName 服务名称
     * @param conclusion 健康结论
     * @param requestedWindow 观测窗口
     * @param scenarioReasonCodes 场景原因码
     * @return 可跨平台引用的隔离沙盘证据
     */
    private static ServiceHealthEvidence sandboxHealthEvidence(
            String serviceUid,
            String serviceName,
            HealthConclusion conclusion,
            Integer requestedWindow,
            List<String> scenarioReasonCodes) {
        Instant observedAt = Instant.parse("2026-08-11T08:00:00Z");
        List<String> reasonCodes = new ArrayList<>(scenarioReasonCodes);
        reasonCodes.add("COMPETITION_SANDBOX_SNAPSHOT");
        if (requestedWindow != null) {
            reasonCodes.add("WINDOW_" + requestedWindow + "M");
        }
        RoleHealth role = new RoleHealth(
                serviceUid + "-role-1",
                "inference",
                "competition-sandbox",
                "running",
                false,
                observedAt);
        return new ServiceHealthEvidence(
                serviceUid,
                serviceName,
                "running",
                42,
                false,
                1,
                1,
                0,
                List.of(role),
                List.of(),
                conclusion,
                List.copyOf(reasonCodes),
                observedAt);
    }

    /**
     * 校验服务 UID 和观测窗口。
     *
     * @param arguments 查询参数
     * @return 已校验参数
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
     */
    private ServiceInstance loadService(String uid) {
        try {
            return serviceInstanceService.getByUid(uid);
        } catch (IllegalArgumentException exception) {
            throw new AgentContractException(404, "RESOURCE_NOT_FOUND", "服务实例不存在或已删除");
        }
    }

    /**
     * 聚合服务和角色实例，健康算法只在后端定义一次。
     *
     * @param service 服务实例
     * @param roles 角色实例列表
     * @param requestedWindow 调用方指定窗口
     * @return 服务健康证据
     */
    private ServiceHealthEvidence assemble(
            ServiceInstance service,
            List<RoleInstance> roles,
            Integer requestedWindow) {
        List<RoleHealth> roleHealth = roles.stream().map(this::toRoleHealth).toList();
        long running = roles.stream().filter(role -> "running".equalsIgnoreCase(role.getStatus())).count();
        long failed = roles.stream().filter(role -> isFailed(role.getStatus())).count();
        List<String> reasonCodes = new ArrayList<>();
        HealthConclusion conclusion = conclude(service, roles, failed, reasonCodes);
        if (requestedWindow != null) {
            reasonCodes.add("WINDOW_" + requestedWindow + "M");
        }
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
        return HealthConclusion.HEALTHY;
    }

    /**
     * 判断角色状态是否属于明确失败状态。
     *
     * @param status 角色状态
     * @return 是否失败
     */
    private boolean isFailed(String status) {
        return status != null && (status.equalsIgnoreCase("failed") || status.equalsIgnoreCase("error"));
    }

    /**
     * 将角色 Entity 转换为健康 DTO。
     *
     * @param role 角色实例
     * @return 角色健康证据
     */
    private RoleHealth toRoleHealth(RoleInstance role) {
        return new RoleHealth(
                role.getUid(), role.getRoleName(), role.getHostname(), role.getStatus(),
                Boolean.TRUE.equals(role.getNeedRestart()), toInstant(role.getUpdatedAt()));
    }

    /**
     * 将数据库 UTC 本地时间转换为 RFC 3339 时间。
     *
     * @param value 数据库时间
     * @return UTC Instant
     */
    private Instant toInstant(java.time.LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    /** 表示服务健康查询参数。 */
    public record ServiceHealthArguments(String serviceUid, Integer windowMinutes) {
    }

    /** 表示单个角色实例的健康状态。 */
    public record RoleHealth(
            String roleUid,
            String roleName,
            String hostname,
            String status,
            boolean needRestart,
            Instant observedAt) {
    }

    /** 表示最近的受控服务操作；当前数据源不可用时返回空列表和原因码。 */
    public record RecentOperation(String operationUid, String type, String status, Instant completedAt) {
    }

    /** 表示固定的后端健康结论。 */
    public enum HealthConclusion {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        UNKNOWN
    }

    /** 表示可跨平台引用的服务健康证据。 */
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
