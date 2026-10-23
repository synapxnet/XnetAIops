/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 资源版本治理与受限迁移 / Resource version governance and bounded migration.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-15
 * Version: 1.0.0 | Security Level: INTERNAL
 * __version__: 1.0.0 | __author__: maoyo | __copyright__: Copyright 2026 Synapxnet
 * __maintainer__: maoyo | __email__: synapxnet@gmail.com
 */
package com.synapxnet.goai.contract;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 为计划写维护真实版本、演练版本和精确回放。 / Retain live versions, rehearsal versions and exact plan-write replay.
 */
public final class GovernedResourceVersionTracker {

    private final Map<String, Long> liveVersions = new ConcurrentHashMap<>();
    private final Map<String, Long> rehearsalVersions = new ConcurrentHashMap<>();
    private final Map<String, ExecutionSnapshot> executions = new ConcurrentHashMap<>();

    /** 明确注册平台控制的初始版本，不覆盖已有值。 / Register a platform-owned initial version without replacing an existing value. */
    public synchronized void initializeResource(String workspaceId, String resourceId, long initialVersion) {
        if (initialVersion < 1) throw new IllegalArgumentException("Initial resource version must be positive.");
        liveVersions.putIfAbsent(resourceKey(workspaceId, resourceId), initialVersion);
    }

    /** 读取同一个写入版本源，未注册时拒绝。 / Read the write-side version source and reject unregistered resources. */
    public synchronized long currentVersion(String workspaceId, String resourceId) {
        Long version = liveVersions.get(resourceKey(workspaceId, resourceId));
        if (version == null) throw new AgentContractException(409, "RESOURCE_NOT_REGISTERED", "目标资源尚未注册");
        return version;
    }

    /** 在版本锁内读取领域状态和版本。 / Read domain state and versions under the same version lock. */
    public synchronized <T> T readConsistently(Supplier<T> reader) {
        return reader.get();
    }

    /** 深拷贝全部治理状态以供受限迁移。 / Deep-copy all governed state for a bounded migration. */
    public synchronized StateSnapshot snapshot() {
        Map<String, ExecutionSnapshot> copied = new LinkedHashMap<>();
        for (Map.Entry<String, ExecutionSnapshot> entry : executions.entrySet()) {
            copied.put(entry.getKey(), copyExecution(entry.getValue()));
        }
        return new StateSnapshot(1, Map.copyOf(liveVersions), Map.copyOf(rehearsalVersions), Map.copyOf(copied));
    }

    /** 整体校验后恢复到空目标，禁止覆盖或部分恢复。 / Validate the entire snapshot before restoring an empty target. */
    public synchronized void restore(StateSnapshot snapshot) {
        if (!liveVersions.isEmpty() || !rehearsalVersions.isEmpty() || !executions.isEmpty()) {
            throw new IllegalStateException("Governed state can only be restored into an empty tracker.");
        }
        if (snapshot == null || snapshot.schemaVersion() != 1 || snapshot.liveVersions() == null
                || snapshot.rehearsalVersions() == null || snapshot.executions() == null) {
            throw new IllegalArgumentException("Governed snapshot schema is invalid.");
        }
        Map<String, Long> live = validatedVersions(snapshot.liveVersions());
        for (String key : live.keySet()) {
            String[] parts = key.split(":", 2);
            if (parts.length != 2 || !key.equals(resourceKey(parts[0], parts[1]))) {
                throw new IllegalArgumentException("Live resource key is invalid.");
            }
        }
        Map<String, Long> rehearsals = validatedVersions(snapshot.rehearsalVersions());
        for (Map.Entry<String, Long> entry : rehearsals.entrySet()) {
            String[] parts = entry.getKey().split(":", 4);
            if (parts.length != 4 || blank(parts[1]) || blank(parts[2])) {
                throw new IllegalArgumentException("Rehearsal resource key is invalid.");
            }
            Long baseline = live.get(resourceKey(parts[0], parts[3]));
            // 其他审批的历史演练可能落后于最新真实版本。 / Retained rehearsals may lag newer live writes by another approval.
            if (baseline == null) {
                throw new IllegalArgumentException("Rehearsal resource has no matching live baseline.");
            }
        }
        Map<String, ExecutionSnapshot> restored = new LinkedHashMap<>();
        if (snapshot.executions().size() > 100_000) throw new IllegalArgumentException("Execution snapshot exceeds its budget.");
        for (Map.Entry<String, ExecutionSnapshot> entry : snapshot.executions().entrySet()) {
            requireSnapshotText(entry.getKey());
            String[] key = entry.getKey().split(":", 3);
            ExecutionSnapshot execution = copyExecution(entry.getValue());
            if (key.length != 3 || blank(key[1]) || blank(key[2])) {
                throw new IllegalArgumentException("Execution identity key is invalid.");
            }
            Long current = live.get(resourceKey(key[0], execution.resourceId()));
            if (current == null || (!execution.dryRun() && execution.afterVersion() > current)) {
                throw new IllegalArgumentException("Execution resource version is absent or inconsistent.");
            }
            restored.put(entry.getKey(), execution);
        }
        liveVersions.putAll(live);
        rehearsalVersions.putAll(rehearsals);
        executions.putAll(restored);
    }

    /**
     * 在同一临界区内校验版本、执行变更并保存幂等结果。 / Validate, mutate and retain idempotency results in one critical section.
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
        ExecutionSnapshot previous = executions.get(executionKey);
        if (previous != null) {
            assertReplayScope(previous, body);
            return new Execution(copyData(previous.data()), previous.beforeVersion(), previous.afterVersion(), true);
        }
        long expectedVersion = parseVersion(body.expectedResourceVersion());
        boolean dryRun = Boolean.TRUE.equals(body.dryRun());
        String liveKey = resourceKey(context.workspaceId(), body.resourceId());
        String rehearsalKey = rehearsalKey(context, body);
        long beforeVersion;
        long afterVersion;
        Map<String, Object> data;
        if (dryRun) {
            long liveVersion = currentVersion(context.workspaceId(), body.resourceId());
            beforeVersion = rehearsalVersions.getOrDefault(rehearsalKey, liveVersion);
            assertExpectedVersion(beforeVersion, expectedVersion);
            afterVersion = nextVersion(beforeVersion);
            rehearsalVersions.put(rehearsalKey, afterVersion);
            data = Map.of("status", "DRY_RUN");
        } else {
            beforeVersion = currentVersion(context.workspaceId(), body.resourceId());
            assertExpectedVersion(beforeVersion, expectedVersion);
            afterVersion = nextVersion(beforeVersion);
            data = copyData(mutation.get());
            liveVersions.put(liveKey, afterVersion);
            clearRehearsal(context, body.approvalId());
        }
        executions.put(executionKey, new ExecutionSnapshot(
                body.stepId(), body.resourceId(), body.expectedResourceVersion(), dryRun,
                data, beforeVersion, afterVersion));
        return new Execution(data, beforeVersion, afterVersion, false);
    }

    /** 校验版本跟踪所需最小范围。 / Validate the minimum scope required for version tracking. */
    private void requireScope(AgentContract.RequestContext context, AgentContract.ToolRequest<?> body) {
        if (context == null || body == null
                || blank(context.workspaceId()) || blank(context.incidentId())
                || blank(body.approvalId()) || blank(body.stepId())
                || blank(body.resourceId()) || blank(body.idempotencyKey())) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "资源版本治理范围不完整");
        }
    }

    /** 校验幂等键的原有治理范围。 / Validate the original governance scope of an idempotency key. */
    private void assertReplayScope(ExecutionSnapshot previous, AgentContract.ToolRequest<?> body) {
        if (!previous.stepId().equals(body.stepId())
                || !previous.resourceId().equals(body.resourceId())
                || !previous.expectedResourceVersion().equals(body.expectedResourceVersion())
                || previous.dryRun() != Boolean.TRUE.equals(body.dryRun())) {
            throw new AgentContractException(409, "IDEMPOTENCY_CONFLICT", "幂等键已被不同治理请求占用");
        }
    }

    /** 清理同一审批演练版本。 / Clear rehearsal versions belonging to the same approval. */
    private void clearRehearsal(AgentContract.RequestContext context, String approvalId) {
        String prefix = context.workspaceId() + ":" + context.incidentId() + ":" + approvalId + ":";
        rehearsalVersions.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /** 构造范围内幂等执行键。 / Build a workspace and incident scoped idempotency key. */
    private String executionKey(AgentContract.RequestContext context, String idempotencyKey) {
        return context.workspaceId() + ":" + context.incidentId() + ":" + idempotencyKey;
    }

    /** 构造审批和资源共享演练键。 / Build the rehearsal key shared by one approval and resource. */
    private String rehearsalKey(AgentContract.RequestContext context, AgentContract.ToolRequest<?> body) {
        return context.workspaceId() + ":" + context.incidentId() + ":"
                + body.approvalId() + ":" + body.resourceId();
    }

    /** 校验预期版本与当前版本一致。 / Require the expected and current versions to match. */
    private void assertExpectedVersion(long currentVersion, long expectedVersion) {
        if (currentVersion != expectedVersion) {
            throw new AgentContractException(409, "RESOURCE_VERSION_CONFLICT", "目标资源版本已变化");
        }
    }

    /** 检查递增不会溢出后生成下一版本。 / Check for overflow before generating the next version. */
    private long nextVersion(long version) {
        if (version == Long.MAX_VALUE) {
            throw new AgentContractException(409, "RESOURCE_VERSION_EXHAUSTED", "资源版本已达到可用上限");
        }
        return version + 1;
    }

    /** 解析正整数资源版本。 / Parse a positive integer resource version. */
    private long parseVersion(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 1) throw new NumberFormatException();
            return parsed;
        } catch (RuntimeException exception) {
            throw new AgentContractException(400, "INVALID_ARGUMENT", "expectedResourceVersion 必须为正整数");
        }
    }

    /** 判断文本是否为空。 / Check whether a text value is blank. */
    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** 构造兼容旧记录的规范资源键。 / Build a canonical resource key compatible with retained records. */
    private static String resourceKey(String workspaceId, String resourceId) {
        requireSnapshotText(workspaceId);
        requireSnapshotText(resourceId);
        if (workspaceId.contains(":")) throw new IllegalArgumentException("Workspace key cannot contain a delimiter.");
        return workspaceId + ":" + resourceId;
    }

    /** 校验有界版本映射并复制。 / Validate and copy a bounded version map. */
    private static Map<String, Long> validatedVersions(Map<String, Long> values) {
        if (values.size() > 100_000) throw new IllegalArgumentException("Version snapshot exceeds its budget.");
        Map<String, Long> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            requireSnapshotText(entry.getKey());
            if (entry.getValue() == null || entry.getValue() < 1) throw new IllegalArgumentException("Snapshot version must be positive.");
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    /** 校验并深拷贝一条历史执行结果。 / Validate and deep-copy one retained execution result. */
    private static ExecutionSnapshot copyExecution(ExecutionSnapshot value) {
        if (value == null) throw new IllegalArgumentException("Execution snapshot is missing.");
        requireSnapshotText(value.stepId());
        requireSnapshotText(value.resourceId());
        long expected;
        try { expected = Long.parseLong(value.expectedResourceVersion()); }
        catch (RuntimeException error) { throw new IllegalArgumentException("Execution expected version is invalid."); }
        if (expected < 1 || value.beforeVersion() != expected || expected == Long.MAX_VALUE || value.afterVersion() != expected + 1) {
            throw new IllegalArgumentException("Execution version transition is invalid.");
        }
        return new ExecutionSnapshot(value.stepId(), value.resourceId(), value.expectedResourceVersion(), value.dryRun(),
                copyData(value.data()), value.beforeVersion(), value.afterVersion());
    }

    /** 校验迁移键为有界普通文本。 / Validate migration keys as bounded plain text. */
    private static void requireSnapshotText(String value) {
        if (blank(value) || value.length() > 4096 || value.indexOf('\0') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Governed snapshot key is invalid.");
        }
    }

    /** 深拷贝可序列化领域数据。 / Deep-copy JSON-compatible domain data. */
    private static Map<String, Object> copyData(Map<String, Object> data) {
        if (data == null) throw new IllegalArgumentException("Execution data is missing.");
        @SuppressWarnings("unchecked") Map<String, Object> copy = (Map<String, Object>) copyJson(data, 0);
        return copy;
    }

    /** 递归复制 JSON 值并限制深度与集合大小。 / Copy JSON values with bounded nesting and collection sizes. */
    private static Object copyJson(Object value, int depth) {
        if (depth > 32) throw new IllegalArgumentException("Execution data exceeds its nesting budget.");
        if (value == null || value instanceof String || value instanceof Boolean) return value;
        if (value instanceof Number number) {
            if (!(number instanceof Byte || number instanceof Short || number instanceof Integer
                    || number instanceof Long || number instanceof Float || number instanceof Double
                    || number.getClass() == java.math.BigInteger.class || number.getClass() == java.math.BigDecimal.class)) {
                throw new IllegalArgumentException("Execution data contains an unsupported numeric type.");
            }
            if (!Double.isFinite(number.doubleValue())) throw new IllegalArgumentException("Execution data contains a non-finite number.");
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            if (map.size() > 10_000) throw new IllegalArgumentException("Execution map exceeds its budget.");
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("Execution data keys must be text.");
                copy.put(key, copyJson(entry.getValue(), depth + 1));
            }
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            if (list.size() > 10_000) throw new IllegalArgumentException("Execution list exceeds its budget.");
            List<Object> copy = new ArrayList<>();
            for (Object item : list) copy.add(copyJson(item, depth + 1));
            return Collections.unmodifiableList(copy);
        }
        throw new IllegalArgumentException("Execution data is not JSON-compatible.");
    }

    /** 表示一次可审计版本变更。 / Represent one auditable resource version transition. */
    public record Execution(Map<String, Object> data, long beforeVersion, long afterVersion, boolean replayed) { }

    /** 保存兼容旧幂等记录的字段。 / Retain fields compatible with prior idempotency records. */
    public record ExecutionSnapshot(
            String stepId,
            String resourceId,
            String expectedResourceVersion,
            boolean dryRun,
            Map<String, Object> data,
            long beforeVersion,
            long afterVersion) { }

    /** 迁移版本、演练分支与完整幂等记录。 / Transfer versions, rehearsal branches and complete idempotency records. */
    public record StateSnapshot(int schemaVersion, Map<String, Long> liveVersions,
            Map<String, Long> rehearsalVersions, Map<String, ExecutionSnapshot> executions) { }
}
