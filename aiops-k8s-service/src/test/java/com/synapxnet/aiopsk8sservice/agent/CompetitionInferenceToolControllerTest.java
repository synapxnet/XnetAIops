/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-15
 * Version: 1.0.0 | Security Level: INTERNAL
 * __version__: 1.0.0 | __author__: maoyo | __copyright__: Copyright 2026 Synapxnet
 * __maintainer__: maoyo | __email__: synapxnet@gmail.com
 */
package com.synapxnet.aiopsk8sservice.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapxnet.goai.contract.AgentContract;
import com.synapxnet.goai.contract.AgentContractException;
import com.synapxnet.goai.contract.GovernedApprovalVerifier;
import com.synapxnet.goai.contract.GovernedResourceVersionTracker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/**
 * 验证推荐推理完整恢复计划与独立恢复谓词保持一致。 / Verify the complete recovery plan against independent recovery predicates.
 */
class CompetitionInferenceToolControllerTest {
    @TempDir Path temporary;

    /** 全局懒加载下迁移必须在首次业务访问前完成，且无关Bean继续懒加载；Migration must complete before first access under global lazy initialization while unrelated beans remain lazy. */
    @Test
    void lazyApplicationRestoresBeforeAnyBusinessAccess() throws Exception {
        var original = controller();
        tune(original, "inc_original", "startup-retained", "42", false);
        Map<String, Object> document = Map.of(
                "schema", "openxnet.governed-state-export.v1", "platform", "aiops",
                "controller", CompetitionInferenceToolController.class.getName(),
                "sourceJarSha256", "48325d55b67d06c9e9508b340c24a5839dc352f1d8c33c07a22707c3b48b7394",
                "domainField", "incidentStates", "readOnly", true, "exportedAt", "2026-09-15T00:00:00Z",
                "tracker", tracker(original).snapshot(),
                "domainBindings", Map.of("inc_original", Map.of("workspaceId", "ws_goai_demo", "serviceUid", "service_rec_inference")),
                "domainState", Map.of("inc_original", new CompetitionInferenceToolController.InferenceSnapshot(
                        2, 6, 16, 0, "DYNAMIC_SHAPE_OPTIMIZED", false, false, false)));
        Path file = temporary.resolve("startup.json");
        byte[] bytes = new ObjectMapper().writeValueAsBytes(document);
        Files.write(file, bytes);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        try (var context = lazyContext(file, digest)) {
            context.refresh();
            assertTrue(Files.exists(temporary.resolve("startup.json.consumed")));
            assertFalse(context.getBeanFactory().containsSingleton("unrelatedLazyBean"));
            assertEquals(tracker(original).snapshot(), tracker(context.getBean(CompetitionInferenceToolController.class)).snapshot());
        }
    }

    /** 错误迁移在真实Spring懒加载刷新阶段失败，不等到请求到达；Invalid migration fails during the actual lazy Spring refresh before any request. */
    @Test
    void lazyApplicationRejectsInvalidMigrationDuringRefresh() throws Exception {
        Path file = temporary.resolve("bad-startup.json");
        Files.writeString(file, "{}");
        try (var context = lazyContext(file, "0".repeat(64))) {
            assertThrows(org.springframework.beans.BeansException.class, context::refresh);
            assertFalse(Files.exists(temporary.resolve("bad-startup.json.consumed")));
        }
    }

    /** 使用Spring Boot真实全局懒加载处理器，仅替代外部审批边界；Use Spring Boot's actual global lazy processor and mock only the external approval boundary. */
    private org.springframework.context.annotation.AnnotationConfigApplicationContext lazyContext(Path file, String digest) {
        var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("migration-test",
                Map.of("goai.resource-state-migration-file", file.toString(), "goai.resource-state-migration-sha256", digest)));
        context.addBeanFactoryPostProcessor(new org.springframework.boot.LazyInitializationBeanFactoryPostProcessor());
        context.registerBean(GovernedApprovalVerifier.class, this::verifier);
        context.registerBean("unrelatedLazyBean", StringBuilder.class);
        context.register(CompetitionInferenceToolController.class);
        return context;
    }

    /** 不同资源版本分叉后，读取必须逐项对应真实CAS；Read each actual CAS version after different resources diverge. */
    @Test
    void exposesDivergedVersionsAndRetainsStateAcrossIncidents() {
        var controller = controller();
        tune(controller, "inc_original", "tune1", "42", false);
        tune(controller, "inc_original", "tune2", "43", false);
        var response = metrics(controller, "inc_next");
        Map<?, ?> versions = (Map<?, ?>) response.data().get("resourceVersions");
        assertEquals("44", versions.get("deploy_recommendation_prod/runtime"));
        assertEquals("42", versions.get("3/gpu-prewarmed"));
        assertEquals("42", versions.get("3/recommendation-prod/recommendation-inference"));
        assertEquals("44", response.meta().resourceVersion());
        assertEquals("DYNAMIC_SHAPE_OPTIMIZED", recovery(controller, "inc_next").data().get("runtimeProfile"));
        assertThrows(AgentContractException.class, () -> tune(controller, "inc_next", "stale", "42", false));
    }

    /** 演练不改变公开领域状态或版本；Dry runs must not alter public domain state or versions. */
    @Test
    void keepsDryRunSeparateFromLiveReads() {
        var controller = controller();
        tune(controller, "inc_original", "dry1", "42", true);
        tune(controller, "inc_original", "dry2", "43", true);
        assertEquals("42", metrics(controller, "inc_next").meta().resourceVersion());
        assertEquals("BASELINE", recovery(controller, "inc_next").data().get("runtimeProfile"));
    }

    /** 已记录幂等请求不得再次递增版本；A retained idempotency replay must not increment the version again. */
    @Test
    void replaysOriginalWriteWithoutAnotherMutation() {
        var controller = controller();
        var first = tune(controller, "inc_original", "once", "42", false);
        var repeated = tune(controller, "inc_original", "once", "42", false);
        assertEquals(first.data(), repeated.data());
        assertEquals("43", metrics(controller, "inc_next").meta().resourceVersion());
    }

    /** 参数不能以另一个审批资源身份修改领域状态；Arguments cannot mutate domain state under a different approved resource identity. */
    @Test
    void rejectsMismatchedCanonicalTarget() {
        var controller = controller();
        String tool = "aiops.inference.runtime.tune";
        var body = request(tool, "bad", "different/runtime", "42", false,
                new CompetitionInferenceToolController.RuntimeTuneArguments("deploy_recommendation_prod", 16, 500, "DYNAMIC_SHAPE_OPTIMIZED"));
        assertThrows(AgentContractException.class, () -> controller.tuneRuntime(body, servlet("inc_original", tool, "bad")));
        assertEquals("42", metrics(controller, "inc_original").meta().resourceVersion());
    }

    /** 未登记服务和部署组合不能获得其他目标版本；Unknown service/deployment pairs cannot read another target's versions. */
    @Test
    void rejectsUnknownReadTarget() {
        var controller = controller();
        String tool = "aiops.inference.metrics.get";
        var body = request(tool, "unknown", null, null, false,
                new CompetitionInferenceToolController.InferenceMetricsArguments("service_rec_inference", "deploy_risk_prod", 15));
        assertThrows(AgentContractException.class, () -> controller.metrics(body, servlet("inc_original", tool, "unknown")));
    }

    /** 迁移完整保留版本与幂等历史，同时防止重复启动导入旧状态；Migration preserves versions and replay history and rejects repeated stale startup imports. */
    @Test
    void migratesOriginalStateAndRejectsConsumedSnapshot() throws Exception {
        var original = controller();
        tune(original, "inc_original", "migrate", "42", false);
        var tracker = tracker(original).snapshot();
        Map<String, Object> document = Map.of(
                "schema", "openxnet.governed-state-export.v1", "platform", "aiops",
                "controller", CompetitionInferenceToolController.class.getName(),
                "sourceJarSha256", "48325d55b67d06c9e9508b340c24a5839dc352f1d8c33c07a22707c3b48b7394",
                "domainField", "incidentStates", "readOnly", true, "exportedAt", "2026-09-15T00:00:00Z",
                "tracker", tracker,
                "domainBindings", Map.of("inc_original", Map.of("workspaceId", "ws_goai_demo", "serviceUid", "service_rec_inference")),
                "domainState", Map.of("inc_original", new CompetitionInferenceToolController.InferenceSnapshot(
                        2, 6, 16, 0, "DYNAMIC_SHAPE_OPTIMIZED", false, false, false)));
        Path file = temporary.resolve("migration.json");
        byte[] bytes = new ObjectMapper().writeValueAsBytes(document);
        Files.write(file, bytes);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        var migrated = new CompetitionInferenceToolController(verifier(), file.toString(), digest);
        assertEquals(tracker, tracker(migrated).snapshot());
        assertEquals("43", metrics(migrated, "inc_next").meta().resourceVersion());
        assertEquals("DYNAMIC_SHAPE_OPTIMIZED", recovery(migrated, "inc_next").data().get("runtimeProfile"));
        tune(migrated, "inc_original", "migrate", "42", false);
        assertEquals("43", metrics(migrated, "inc_next").meta().resourceVersion());
        assertThrows(IllegalStateException.class, () -> new CompetitionInferenceToolController(verifier(), file.toString(), digest));
    }

    /** 错误摘要不得消费迁移文件或开始提供服务；A digest mismatch must not consume the migration file or serve traffic. */
    @Test
    void rejectsTamperedMigrationBeforeConsumption() throws Exception {
        Path file = temporary.resolve("tampered.json");
        Files.writeString(file, "{}");
        assertThrows(IllegalStateException.class, () -> new CompetitionInferenceToolController(verifier(), file.toString(), "b".repeat(64)));
        assertFalse(Files.exists(temporary.resolve("tampered.json.consumed")));
    }

    /** 创建只替代审批远端的真实Controller；Create the real controller with only the external approval boundary mocked. */
    private CompetitionInferenceToolController controller() { return new CompetitionInferenceToolController(verifier()); }

    /** 建立确定性审批替身，业务CAS和状态仍真实执行；Provide deterministic approval while retaining real CAS and domain mutations. */
    private GovernedApprovalVerifier verifier() {
        var verifier = mock(GovernedApprovalVerifier.class);
        when(verifier.verify(any(), any())).thenReturn(new GovernedApprovalVerifier.ApprovalDecision("approval", "approver", "executor", "arguments-digest"));
        return verifier;
    }

    /** 用明确incident执行一条真实运行时调优；Execute one real runtime mutation for an explicit incident. */
    private AgentContract.ToolResponse<Map<String, Object>> tune(CompetitionInferenceToolController controller, String incident, String key, String version, boolean dryRun) {
        String tool = "aiops.inference.runtime.tune";
        return controller.tuneRuntime(request(tool, key, "deploy_recommendation_prod/runtime", version, dryRun,
                new CompetitionInferenceToolController.RuntimeTuneArguments("deploy_recommendation_prod", 16, 500, "DYNAMIC_SHAPE_OPTIMIZED")), servlet(incident, tool, key));
    }

    /** 查询本轮实际指标包络；Read the actual metrics envelope for this test. */
    private AgentContract.ToolResponse<Map<String, Object>> metrics(CompetitionInferenceToolController controller, String incident) {
        String tool = "aiops.inference.metrics.get";
        return controller.metrics(request(tool, "read", null, null, false,
                new CompetitionInferenceToolController.InferenceMetricsArguments("service_rec_inference", "deploy_recommendation_prod", 15)), servlet(incident, tool, "read"));
    }

    /** 查询真实恢复状态，验证跨incident领域连续性；Read recovery state to verify domain continuity across incidents. */
    private AgentContract.ToolResponse<Map<String, Object>> recovery(CompetitionInferenceToolController controller, String incident) {
        String tool = "aiops.inference.recovery.status";
        return controller.recoveryStatus(request(tool, "status", null, null, false,
                new CompetitionInferenceToolController.RecoveryStatusArguments("service_rec_inference", "deploy_recommendation_prod")), servlet(incident, tool, "status"));
    }

    /** 创建已鉴权请求上下文替身；Create an authenticated request-context fixture. */
    private MockHttpServletRequest servlet(String incident, String tool, String key) {
        var servlet = new MockHttpServletRequest();
        servlet.setAttribute(AgentContract.CONTEXT_ATTRIBUTE, new AgentContract.RequestContext("ws_goai_demo", incident, "trace", tool, key, "operator", "request"));
        return servlet;
    }

    /** 创建类型化治理请求；Create a typed governed request. */
    private <T> AgentContract.ToolRequest<T> request(String tool, String key, String resourceId, String version, boolean dryRun, T arguments) {
        return new AgentContract.ToolRequest<>("request-" + key, tool, arguments, "approval", "plan", "digest", key, resourceId, 1L, version, "arguments-digest", false, "test", key, dryRun);
    }

    /** 仅供测试取得内部tracker来比较完整迁移状态；Inspect the tracker only in tests to compare complete migration state. */
    private GovernedResourceVersionTracker tracker(CompetitionInferenceToolController controller) throws Exception {
        var field = CompetitionInferenceToolController.class.getDeclaredField("versionTracker");
        field.setAccessible(true);
        return (GovernedResourceVersionTracker) field.get(controller);
    }

    /** 完成容量、流量、弹性和动态形状优化收敛后必须判定恢复。 / Accept recovery after full capacity, traffic, autoscaling and profile convergence. */
    @Test
    void acceptsFinalPlannedState() {
        assertTrue(CompetitionInferenceToolController.hasRecoveredInferenceState(
                4, 12, 64, 100, true, true, "DYNAMIC_SHAPE_OPTIMIZED"));
    }

    /** 紧急调优阶段的批大小尚未恢复时不得提前关闭事件。 / Reject early closure before restoring the stable batch size. */
    @Test
    void rejectsEmergencyBatchSize() {
        assertFalse(CompetitionInferenceToolController.hasRecoveredInferenceState(
                4, 20, 16, 100, true, true, "DYNAMIC_SHAPE_OPTIMIZED"));
    }

    /** 未完成容量收敛时不得仅凭指标改善关闭事件。 / Reject closure before capacity convergence. */
    @Test
    void rejectsUnconvergedCapacity() {
        assertFalse(CompetitionInferenceToolController.hasRecoveredInferenceState(
                4, 12, 64, 100, true, false, "DYNAMIC_SHAPE_OPTIMIZED"));
    }
}
