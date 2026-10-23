/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 版本一致性与迁移回归 / Version consistency and migration regression.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-15
 * Version: 1.0.0 | Security Level: INTERNAL
 * __version__: 1.0.0 | __author__: maoyo | __copyright__: Copyright 2026 Synapxnet
 * __maintainer__: maoyo | __email__: synapxnet@gmail.com
 */
package com.synapxnet.goai.contract;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** 验证跨 incident、迁移和失败边界。 / Verify cross-incident, migration and failure boundaries. */
class GovernedResourceVersionMigrationTest {
    /** 未登记资源不能接受调用者提供的任意初值。 / A caller cannot seed an unregistered resource version. */
    @Test void rejectsMissingRegistration() {
        var tracker = new GovernedResourceVersionTracker();
        assertThrows(AgentContractException.class, () -> tracker.execute(context("old"), request("987", "first", false, "a"), Map::of));
        assertTrue(tracker.snapshot().liveVersions().isEmpty());
        assertTrue(tracker.snapshot().executions().isEmpty());
    }

    /** 新事件共享当前资源版本，注册不覆盖已有值。 / New incidents share current versions and registration never overwrites them. */
    @Test void preservesLiveVersionAcrossIncidents() {
        var tracker = registered(42);
        tracker.execute(context("old"), request("42", "first", false, "a"), Map::of);
        tracker.initializeResource("ws", "resource", 42);
        assertEquals(43, tracker.currentVersion("ws", "resource"));
        assertThrows(AgentContractException.class, () -> tracker.execute(context("new"), request("42", "next", false, "b"), Map::of));
        tracker.execute(context("new"), request("43", "next", false, "b"), Map::of);
        assertEquals(44, tracker.currentVersion("ws", "resource"));
    }

    /** 演练、失败写和当前版本互不污染。 / Rehearsals, rejected writes and live versions stay isolated. */
    @Test void rejectedWriteDoesNotDiscardRehearsal() {
        var tracker = registered(42);
        tracker.execute(context("old"), request("42", "dry", true, "a"), Map::of);
        var before = tracker.snapshot();
        assertThrows(AgentContractException.class, () -> tracker.execute(context("old"), request("43", "bad", false, "a"), Map::of));
        assertEquals(before, tracker.snapshot());
        assertEquals(42, tracker.currentVersion("ws", "resource"));
    }

    /** 溢出必须在领域修改前拒绝。 / Overflow is rejected before mutating domain state. */
    @Test void rejectsOverflowBeforeMutation() {
        var tracker = registered(Long.MAX_VALUE);
        var mutations = new AtomicInteger();
        assertThrows(AgentContractException.class, () -> tracker.execute(context("old"), request(Long.toString(Long.MAX_VALUE), "first", false, "a"), () -> Map.of("n", mutations.incrementAndGet())));
        assertEquals(0, mutations.get());
        assertEquals(Long.MAX_VALUE, tracker.currentVersion("ws", "resource"));
    }

    /** 迁移保留旧演练、两个领域版本及原幂等结果。 / Migration preserves lagging rehearsals, resource versions and exact replay. */
    @Test void restoresAllStateAndReplaysWithoutMutation() {
        var source = registered(42);
        source.initializeResource("ws", "other", 9);
        source.execute(context("old"), request("42", "dry", true, "old-approval"), Map::of);
        var committed = source.execute(context("new"), request("42", "first", false, "a"), () -> Map.of("items", List.of(Map.of("uid", "retained"))));
        source.execute(context("new"), request("43", "second", false, "a"), Map::of);
        var restored = new GovernedResourceVersionTracker();
        restored.restore(source.snapshot());
        assertEquals(source.snapshot(), restored.snapshot());
        var replay = restored.execute(context("new"), request("42", "first", false, "a"), () -> { throw new AssertionError("Must not execute replay"); });
        assertTrue(replay.replayed());
        assertEquals(committed.data(), replay.data());
        assertEquals(42, replay.beforeVersion());
        assertEquals(43, replay.afterVersion());
        assertEquals(44, restored.currentVersion("ws", "resource"));
        assertEquals(9, restored.currentVersion("ws", "other"));
    }

    /** 原数据变更和调用者修改不能污染迁移与回放。 / Source or caller mutation cannot corrupt retained data or replay. */
    @Test void retainsIndependentDeepCopies() {
        var tracker = registered(42);
        List<Object> items = new ArrayList<>(List.of("original"));
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("items", items);
        var execution = tracker.execute(context("old"), request("42", "first", false, "a"), () -> nested);
        items.add("unexpected");
        nested.put("another", true);
        assertEquals(Map.of("items", List.of("original")), execution.data());
        assertThrows(UnsupportedOperationException.class, () -> execution.data().put("bad", true));
        assertEquals(execution.data(), tracker.snapshot().executions().get("ws:old:first").data());
    }

    /** 损坏快照整体失败，目标保持空状态并可接受正确快照。 / Invalid snapshots fail atomically, leaving a clean restore target. */
    @Test void invalidSnapshotCannotPartiallyRestore() {
        var source = registered(42);
        source.execute(context("old"), request("42", "first", false, "a"), Map::of);
        var snapshot = source.snapshot();
        var target = new GovernedResourceVersionTracker();
        var invalid = new GovernedResourceVersionTracker.StateSnapshot(1, snapshot.liveVersions(), Map.of("ws:old:a:missing", 43L), snapshot.executions());
        assertThrows(IllegalArgumentException.class, () -> target.restore(invalid));
        assertTrue(target.snapshot().liveVersions().isEmpty());
        assertTrue(target.snapshot().executions().isEmpty());
        target.restore(snapshot);
        assertThrows(IllegalStateException.class, () -> target.restore(snapshot));
        assertEquals(snapshot, target.snapshot());
    }

    /** 缺资源与不合法递增不能通过迁移校验。 / Missing resources and invalid transitions cannot pass restore validation. */
    @Test void rejectsOrphanExecutionAndBadTransition() {
        var orphan = new GovernedResourceVersionTracker.ExecutionSnapshot("s", "missing", "42", false, Map.of(), 42, 43);
        var bad = new GovernedResourceVersionTracker.ExecutionSnapshot("s", "resource", "42", false, Map.of(), 42, 44);
        for (var entry : List.of(orphan, bad)) {
            var target = new GovernedResourceVersionTracker();
            assertThrows(IllegalArgumentException.class, () -> target.restore(new GovernedResourceVersionTracker.StateSnapshot(1, Map.of("ws:resource", 44L), Map.of(), Map.of("ws:old:key", entry))));
            assertTrue(target.snapshot().liveVersions().isEmpty());
        }
    }

    /** 创建已注册版本源。 / Create a registered version source. */
    private GovernedResourceVersionTracker registered(long version) {
        var tracker = new GovernedResourceVersionTracker();
        tracker.initializeResource("ws", "resource", version);
        return tracker;
    }

    /** 创建指定事件上下文。 / Create a context for one incident. */
    private AgentContract.RequestContext context(String incident) {
        return new AgentContract.RequestContext("ws", incident, "trace", "tool", "key", "actor", "request");
    }

    /** 创建固定资源治理请求。 / Create a governed request for the fixed resource. */
    private AgentContract.ToolRequest<Object> request(String version, String key, boolean dry, String approval) {
        return new AgentContract.ToolRequest<>("request", "tool", Map.of(), approval, "plan", "digest", key, "resource", 1L, version, "args", false, "test", key, dry);
    }
}
