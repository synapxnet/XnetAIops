package com.synapxnet.goai.contract;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 为计划级写操作维护真实版本、Dry Run 虚拟版本和精确幂等回放。
 */
public final class GovernedResourceVersionTracker {

    private final Map<String, Long> liveVersions = new ConcurrentHashMap<>();
    private final Map<String, Long> rehearsalVersions = new ConcurrentHashMap<>();
    private final Map<String, StoredExecution> executions = new ConcurrentHashMap<>();

    /**
     * 在同一临界区内校验版本、执行变更并保存幂等结果。
     *
     * @param context 已鉴权请求上下文
     * @param body 完整治理请求
     * @param mutation 真实执行时调用的领域变更
     * @return 领域结果和变更前后版本
     */
    public synchronized Execution execute(
            AgentContract.RequestContext context,
            AgentContract.ToolRequest<?> body,
            Supplier<Map<String, Object>> mutation) {
        requireScope(context, body);
        String executionKey = executionKey(context, body.idempotencyKey());
        StoredExecution previous = executions.get(executionKey);
        if (previous != null) {
            assertReplayScope(previous, body);
            return new Execution(previous.data(), previous.beforeVersion(), previous.afterVersion(), true);
        }

        long expectedVersion = parseVersion(body.expectedResourceVersion());
        boolean dryRun = Boolean.TRUE.equals(body.dryRun());
        String liveKey = context.workspaceId() + ":" + body.resourceId();
        String rehearsalKey = rehearsalKey(context, body);
        long beforeVersion;
        long afterVersion;
        Map<String, Object> data;
        if (dryRun) {
            long liveVersion = liveVersions.computeIfAbsent(liveKey, ignored -> expectedVersion);
            beforeVersion = rehearsalVersions.computeIfAbsent(rehearsalKey, ignored -> liveVersion);
            assertExpectedVersion(beforeVersion, expectedVersion);
            afterVersion = beforeVersion + 1;
            rehearsalVersions.put(rehearsalKey, afterVersion);
            data = Map.of("status", "DRY_RUN");
        } else {
            clearRehearsal(context, body.approvalId());
            beforeVersion = liveVersions.computeIfAbsent(liveKey, ignored -> expectedVersion);
            assertExpectedVersion(beforeVersion, expectedVersion);
            data = Map.copyOf(mutation.get());
            afterVersion = beforeVersion + 1;
            liveVersions.put(liveKey, afterVersion);
        }
        executions.put(executionKey, new StoredExecution(
                body.stepId(), body.resourceId(), body.expectedResourceVersion(), dryRun,
                data, beforeVersion, afterVersion));
        return new Execution(data, beforeVersion, afterVersion, false);
    }

    /** 校验请求包含资源版本跟踪所需的最小治理范围。 */
    private void requireScope(
            AgentContract.RequestContext context,
            AgentContract.ToolRequest<?> body) {
        if (context == null || body == null
                || blank(context.workspaceId()) || blank(context.incidentId())
                || blank(body.approvalId()) || blank(body.stepId())
                || blank(body.resourceId()) || blank(body.idempotencyKey())) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "资源版本治理范围不完整");
        }
    }

    /** 校验相同幂等键没有被不同步骤、资源、版本或执行模式复用。 */
    private void assertReplayScope(StoredExecution previous, AgentContract.ToolRequest<?> body) {
        if (!previous.stepId().equals(body.stepId())
                || !previous.resourceId().equals(body.resourceId())
                || !previous.expectedResourceVersion().equals(body.expectedResourceVersion())
                || previous.dryRun() != Boolean.TRUE.equals(body.dryRun())) {
            throw new AgentContractException(409, "IDEMPOTENCY_CONFLICT", "幂等键已被不同治理请求占用");
        }
    }

    /** 清理同一审批的虚拟演练版本，真实执行始终从真实资源版本开始。 */
    private void clearRehearsal(
            AgentContract.RequestContext context,
            String approvalId) {
        String prefix = context.workspaceId() + ":" + context.incidentId() + ":" + approvalId + ":";
        rehearsalVersions.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /** 构造绑定 Workspace、Incident 和调用幂等键的执行键。 */
    private String executionKey(AgentContract.RequestContext context, String idempotencyKey) {
        return context.workspaceId() + ":" + context.incidentId() + ":" + idempotencyKey;
    }

    /** 构造同一审批和资源内共享的演练版本键。 */
    private String rehearsalKey(
            AgentContract.RequestContext context,
            AgentContract.ToolRequest<?> body) {
        return context.workspaceId() + ":" + context.incidentId() + ":"
                + body.approvalId() + ":" + body.resourceId();
    }

    /** 校验调用方预期版本与当前版本一致。 */
    private void assertExpectedVersion(long currentVersion, long expectedVersion) {
        if (currentVersion != expectedVersion) {
            throw new AgentContractException(409, "RESOURCE_VERSION_CONFLICT", "目标资源版本已变化");
        }
    }

    /** 解析正整数资源版本。 */
    private long parseVersion(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 1) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (RuntimeException exception) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "expectedResourceVersion 必须为正整数");
        }
    }

    /** 判断文本是否为空。 */
    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** 表示一次可审计的资源版本变更结果。 */
    public record Execution(
            Map<String, Object> data,
            long beforeVersion,
            long afterVersion,
            boolean replayed) {
    }

    /** 保存幂等回放所需的完整治理范围和结果。 */
    private record StoredExecution(
            String stepId,
            String resourceId,
            String expectedResourceVersion,
            boolean dryRun,
            Map<String, Object> data,
            long beforeVersion,
            long afterVersion) {
    }
}
