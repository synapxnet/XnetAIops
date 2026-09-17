/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 决赛平台契约与回归支持。 Finals platform contracts and regression support.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-17 | Version: 1.3.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.goai.contract;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 Dry Run 虚拟版本、真实提交和幂等回放互不污染。
 */
class GovernedResourceVersionTrackerTest {

    /** 同一资源的多步 Dry Run 必须按虚拟版本递增且不执行领域变更。 */
    @Test
    void advancesRehearsalVersionsWithoutMutation() {
        GovernedResourceVersionTracker tracker = registeredTracker();
        AtomicInteger mutations = new AtomicInteger();
        GovernedResourceVersionTracker.Execution first = tracker.execute(
                context("idem:step-1"), request("step-1", "42", "idem:step-1", true),
                () -> Map.of("count", mutations.incrementAndGet()));
        GovernedResourceVersionTracker.Execution second = tracker.execute(
                context("idem:step-2"), request("step-2", "43", "idem:step-2", true),
                () -> Map.of("count", mutations.incrementAndGet()));
        assertEquals(0, mutations.get());
        assertEquals(42, first.beforeVersion());
        assertEquals(44, second.afterVersion());
    }

    /** 真实执行必须从真实版本开始并对完全相同的幂等请求回放原结果。 */
    @Test
    void commitsLiveVersionAndReplaysIdempotently() {
        GovernedResourceVersionTracker tracker = registeredTracker();
        AtomicInteger mutations = new AtomicInteger();
        AgentContract.ToolRequest<Object> body = request("step-1", "42", "idem:execute", false);
        GovernedResourceVersionTracker.Execution first = tracker.execute(
                context("idem:execute"), body, () -> Map.of("count", mutations.incrementAndGet()));
        GovernedResourceVersionTracker.Execution replay = tracker.execute(
                context("idem:execute"), body, () -> Map.of("count", mutations.incrementAndGet()));
        assertEquals(1, mutations.get());
        assertEquals(first.data(), replay.data());
        assertTrue(replay.replayed());
    }

    /** 相同幂等键被不同步骤复用时必须拒绝。 */
    @Test
    void rejectsIdempotencyScopeConflict() {
        GovernedResourceVersionTracker tracker = registeredTracker();
        tracker.execute(context("idem:shared"), request("step-1", "42", "idem:shared", false), Map::of);
        assertThrows(AgentContractException.class, () -> tracker.execute(
                context("idem:shared"), request("step-2", "43", "idem:shared", false), Map::of));
    }

    /** 注册由平台控制的测试基线。 / Register a platform-owned baseline for tests. */
    private GovernedResourceVersionTracker registeredTracker() {
        GovernedResourceVersionTracker tracker = new GovernedResourceVersionTracker();
        tracker.initializeResource("ws_goai_demo", "resource", 42L);
        return tracker;
    }

    /** 创建固定测试请求上下文。 / Create a fixed request context. */
    private AgentContract.RequestContext context(String idempotencyKey) {
        return new AgentContract.RequestContext(
                "ws_goai_demo", "inc_tracker_test", "trace_tracker_test",
                "tool.test", idempotencyKey, "operator", "request");
    }

    /** 创建固定资源上的治理请求。 */
    private AgentContract.ToolRequest<Object> request(
            String stepId, String expectedVersion, String idempotencyKey, boolean dryRun) {
        return new AgentContract.ToolRequest<>(
                "request", "tool.test", Map.of(), "approval", "plan", "digest",
                stepId, "resource", 1L, expectedVersion, "arguments-digest",
                false, "test", idempotencyKey, dryRun);
    }
}
