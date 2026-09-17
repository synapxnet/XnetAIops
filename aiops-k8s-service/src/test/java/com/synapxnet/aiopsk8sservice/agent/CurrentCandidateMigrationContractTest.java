/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 当前候选迁移独立合同测试 / Independent current-candidate migration tests.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-15
 * Version: 1.0.0 | Security Level: INTERNAL
 * __version__: 1.0.0 | __author__: maoyo | __copyright__: Copyright 2026 Synapxnet
 * __maintainer__: maoyo | __email__: synapxnet@gmail.com
 */
package com.synapxnet.aiopsk8sservice.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.synapxnet.goai.contract.AgentContract;
import com.synapxnet.goai.contract.AgentContractException;
import com.synapxnet.goai.contract.GovernedApprovalVerifier;
import com.synapxnet.goai.contract.GovernedResourceVersionTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证公开业务行为和迁移后历史连续性。 / Verify public business behavior and history continuity after migration. */
class CurrentCandidateMigrationContractTest {
    private static final String CURRENT_JAR = "65d376a6f561d42360c5fdff002c8a6625c2fe560507bac6f8fe875b7b94e3c6";
    private static final String LEGACY_JAR = "48325d55b67d06c9e9508b340c24a5839dc352f1d8c33c07a22707c3b48b7394";
    private static final String DOMAIN = "ws_goai_demo:service_rec_inference";
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temporary;
    private int sequence;

    /** 真实写入、未完成演练和两个域必须原样恢复，且旧幂等重放不增加版本。 / Restore real writes, outstanding rehearsals and two domains exactly without advancing replayed versions. */
    @Test
    void preservesCompleteStateAndPublicBehaviorAcrossMigration() throws Exception {
        var original = preparedController();
        var originalState = exportCurrent(original);
        var restored = restore(originalState);
        assertEquals(originalState.get("tracker"), exportCurrent(restored).get("tracker"));
        assertEquals(originalState.get("domainState"), exportCurrent(restored).get("domainState"));
        assertEquals(metrics(original, "inc_after", false).data(), metrics(restored, "inc_after", false).data());
        assertEquals(metrics(original, "inc_after", true).data(), metrics(restored, "inc_after", true).data());
        assertEquals("44", metrics(restored, "inc_after", false).meta().resourceVersion());
        var beforeReplay = exportCurrent(restored);
        tune(restored, "inc_retained", "write-first", "42", false);
        assertEquals(beforeReplay.get("tracker"), exportCurrent(restored).get("tracker"));
        assertEquals(beforeReplay.get("domainState"), exportCurrent(restored).get("domainState"));
        assertThrows(AgentContractException.class,
                () -> tune(restored, "inc_new", "different-write", "42", false));
        assertEquals(beforeReplay.get("tracker"), exportCurrent(restored).get("tracker"));
    }

    /** 只读初始化产生的合法域没有保留写入，也必须能够迁移。 / Migrate valid read-initialized domains even when no retained write exists. */
    @Test
    void preservesReadInitializedStateWithoutInventedWrites() throws Exception {
        var original = new CompetitionInferenceToolController(verifier());
        metrics(original, "inc_read", false);
        metrics(original, "inc_read", true);
        var document = exportCurrent(original);
        assertEquals(0, document.path("tracker").path("executions").size());
        assertEquals(2, document.path("domainState").size());
        var migrated = exportCurrent(restore(document));
        assertEquals(document.get("tracker"), migrated.get("tracker"));
        assertEquals(document.get("domainState"), migrated.get("domainState"));
    }

    /** 未知真实来源不能通过文件摘要包装成有效来源。 / Reject unknown source identities even when the file digest is valid. */
    @Test
    void rejectsUnknownSourceIdentity() throws Exception {
        var document = exportCurrent(preparedController());
        document.put("sourceJarSha256", "a".repeat(64));
        assertRejected(document);
    }

    /** 旧来源不能携带规范域键来绕过原inc绑定协议。 / Reject canonical domain keys under the legacy source protocol. */
    @Test
    void rejectsCanonicalKeysClaimingLegacySource() throws Exception {
        var document = exportCurrent(preparedController());
        document.put("sourceJarSha256", LEGACY_JAR);
        document.putObject("domainBindings");
        assertRejected(document);
    }

    /** 当前来源不得混用旧事件键。 / Reject legacy incident keys under the current source protocol. */
    @Test
    void rejectsLegacyKeysClaimingCurrentSource() throws Exception {
        var document = exportCurrent(preparedController());
        var domains = (ObjectNode) document.get("domainState");
        domains.set("inc_retained", domains.remove(DOMAIN));
        assertRejected(document);
    }

    /** 域键必须绑定已登记服务且具有明确workspace边界。 / Require registered services and unambiguous workspace boundaries in domain keys. */
    @Test
    void rejectsUnknownOrAmbiguousCanonicalTargets() throws Exception {
        for (String invalid : List.of("ws_goai_demo:unknown-service", "ws_goai_demo:extra:service_rec_inference", ":service_rec_inference")) {
            var document = exportCurrent(preparedController());
            var domains = (ObjectNode) document.get("domainState");
            domains.set(invalid, domains.remove(DOMAIN));
            assertRejected(document);
        }
    }

    /** 不允许将推荐业务伪标为初始健康来改变恢复判定。 / Reject a forged initially healthy flag that changes recovery semantics. */
    @Test
    void rejectsMismatchedInitialStability() throws Exception {
        var document = exportCurrent(preparedController());
        ((ObjectNode) document.path("domainState").path(DOMAIN)).put("initiallyStable", true);
        assertRejected(document);
    }

    /** 任一已登记域资源版本缺失都不能恢复部分状态。 / Reject partial restoration when any registered domain resource version is missing. */
    @Test
    void rejectsMissingTargetVersion() throws Exception {
        var document = exportCurrent(preparedController());
        ((ObjectNode) document.path("tracker").path("liveVersions"))
                .remove("ws_goai_demo:service_rec_inference/traffic");
        assertRejected(document);
    }

    /** 缺失、null或小数领域字段不得默认为有效整数。 / Reject missing, null or fractional domain values instead of coercing defaults. */
    @Test
    void rejectsIncompleteAndCoercedDomainFields() throws Exception {
        var missing = exportCurrent(preparedController());
        ((ObjectNode) missing.path("domainState").path(DOMAIN)).remove("batchSize");
        assertRejected(missing);
        var nullValue = exportCurrent(preparedController());
        ((ObjectNode) nullValue.path("domainState").path(DOMAIN)).putNull("replicas");
        assertRejected(nullValue);
        var fractional = exportCurrent(preparedController());
        ((ObjectNode) fractional.path("domainState").path(DOMAIN)).put("replicas", 6.5);
        assertRejected(fractional);
    }

    /** 重复JSON键和错文件摘要都不能被消费。 / Reject duplicate JSON keys and incorrect file digests without consuming them. */
    @Test
    void rejectsDuplicateJsonKeysAndDigestMismatch() throws Exception {
        String text = JSON.writeValueAsString(exportCurrent(preparedController()));
        byte[] duplicate = ("{\"platform\":\"wrong\"," + text.substring(1))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path duplicateFile = writeBytes(duplicate);
        assertThrows(IllegalStateException.class,
                () -> new CompetitionInferenceToolController(verifier(), duplicateFile.toString(), digest(duplicate)));
        assertFalse(Files.exists(consumed(duplicateFile)));
        Path changed = writeBytes(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class,
                () -> new CompetitionInferenceToolController(verifier(), changed.toString(), "0".repeat(64)));
        assertFalse(Files.exists(consumed(changed)));
    }

    /** 成功消费后不得重启导入同一快照。 / Reject replaying a successfully consumed migration snapshot. */
    @Test
    void rejectsConsumedCurrentSnapshot() throws Exception {
        byte[] bytes = JSON.writeValueAsBytes(exportCurrent(preparedController()));
        Path file = writeBytes(bytes);
        new CompetitionInferenceToolController(verifier(), file.toString(), digest(bytes));
        assertTrue(Files.exists(consumed(file)));
        assertThrows(IllegalStateException.class,
                () -> new CompetitionInferenceToolController(verifier(), file.toString(), digest(bytes)));
    }

    /** 已服务过的非空目标必须拒绝恢复且原状态不变。 / Reject restoring a nonempty target while preserving its complete prior state. */
    @Test
    void rejectsNonemptyTargetWithoutChangingItsState() throws Exception {
        var target = preparedController();
        var before = exportCurrent(target);
        byte[] bytes = JSON.writeValueAsBytes(before);
        Path file = writeBytes(bytes);
        var entry = CompetitionInferenceToolController.class.getDeclaredMethod("restoreMigration", Path.class, String.class);
        entry.setAccessible(true);
        var failure = assertThrows(InvocationTargetException.class,
                () -> entry.invoke(target, file, digest(bytes)));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertEquals(before, exportCurrent(target));
        assertFalse(Files.exists(consumed(file)));
    }

    /** 从公开真实工具产生两个域、历史写入和未完成演练。 / Produce two domains, retained writes and an unfinished rehearsal through public tools. */
    private CompetitionInferenceToolController preparedController() {
        var controller = new CompetitionInferenceToolController(verifier());
        tune(controller, "inc_retained", "write-first", "42", false);
        tune(controller, "inc_retained", "write-second", "43", false);
        tune(controller, "inc_rehearsal", "dry-pending", "44", true);
        metrics(controller, "inc_read", true);
        return controller;
    }

    /** 仅读取私有状态以模拟实际只读导出，不修改对象。 / Read private state to model the actual read-only export without mutating objects. */
    private ObjectNode exportCurrent(CompetitionInferenceToolController controller) throws Exception {
        var document = JSON.createObjectNode();
        document.put("schema", "openxnet.governed-state-export.v1");
        document.put("platform", "aiops");
        document.put("controller", CompetitionInferenceToolController.class.getName());
        document.put("sourceJarSha256", CURRENT_JAR);
        document.put("domainField", "incidentStates");
        document.put("readOnly", true);
        document.put("exportedAt", "2026-09-15T00:00:00Z");
        var trackerField = CompetitionInferenceToolController.class.getDeclaredField("versionTracker");
        trackerField.setAccessible(true);
        var tracker = (GovernedResourceVersionTracker) trackerField.get(controller);
        document.set("tracker", JSON.valueToTree(tracker.snapshot()));
        var domainsField = CompetitionInferenceToolController.class.getDeclaredField("incidentStates");
        domainsField.setAccessible(true);
        var domains = (Map<?, ?>) domainsField.get(controller);
        var output = document.putObject("domainState");
        for (var item : domains.entrySet()) {
            var state = output.putObject((String) item.getKey());
            for (String name : List.of("gpuNodes", "replicas", "batchSize", "trafficPercent", "runtimeProfile", "autoscalingReady", "converged", "initiallyStable")) {
                var field = item.getValue().getClass().getDeclaredField(name);
                field.setAccessible(true);
                state.set(name, JSON.valueToTree(field.get(item.getValue())));
            }
        }
        return document;
    }

    /** 通过真实启动入口消费一次当前格式文件。 / Consume one current-format file through the real startup entry point. */
    private CompetitionInferenceToolController restore(JsonNode document) throws Exception {
        byte[] bytes = JSON.writeValueAsBytes(document);
        Path file = writeBytes(bytes);
        return new CompetitionInferenceToolController(verifier(), file.toString(), digest(bytes));
    }

    /** 每次拒绝都独立使用新文件并检查未消费。 / Use a fresh file for each rejection and verify it was not consumed. */
    private void assertRejected(JsonNode document) throws Exception {
        byte[] bytes = JSON.writeValueAsBytes(document);
        Path file = writeBytes(bytes);
        assertThrows(IllegalStateException.class,
                () -> new CompetitionInferenceToolController(verifier(), file.toString(), digest(bytes)));
        assertFalse(Files.exists(consumed(file)));
    }

    /** 独占写入本测试的临时快照。 / Exclusively write a snapshot under this test's temporary directory. */
    private Path writeBytes(byte[] bytes) throws Exception {
        Path file = temporary.resolve("current-" + (++sequence) + ".json");
        return Files.write(file, bytes, java.nio.file.StandardOpenOption.CREATE_NEW);
    }

    /** 计算实际文件内容摘要。 / Compute the digest of the actual file content. */
    private String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** 定位正式迁移消费标记。 / Locate the official migration consumption marker. */
    private Path consumed(Path file) { return file.resolveSibling(file.getFileName() + ".consumed"); }

    /** 仅隔离外部审批，保留真实业务状态机。 / Isolate only external approval while retaining the real business state machine. */
    private GovernedApprovalVerifier verifier() {
        var verifier = mock(GovernedApprovalVerifier.class);
        when(verifier.verify(any(), any())).thenReturn(new GovernedApprovalVerifier.ApprovalDecision("approval", "approver", "executor", "arguments-digest"));
        return verifier;
    }

    /** 调用实际调优工具产生或重放一条治理操作。 / Invoke the real tuning tool to create or replay a governed operation. */
    private AgentContract.ToolResponse<Map<String, Object>> tune(CompetitionInferenceToolController controller, String incident, String key, String version, boolean dryRun) {
        String tool = "aiops.inference.runtime.tune";
        return controller.tuneRuntime(request(tool, key, "deploy_recommendation_prod/runtime", version, dryRun,
                new CompetitionInferenceToolController.RuntimeTuneArguments("deploy_recommendation_prod", 16, 500, "DYNAMIC_SHAPE_OPTIMIZED")), servlet(incident, tool, key));
    }

    /** 查询已登记推荐或风控域的实际公开指标。 / Read public metrics for the registered recommendation or risk domain. */
    private AgentContract.ToolResponse<Map<String, Object>> metrics(CompetitionInferenceToolController controller, String incident, boolean risk) {
        String tool = "aiops.inference.metrics.get";
        return controller.metrics(request(tool, "read", null, null, false,
                new CompetitionInferenceToolController.InferenceMetricsArguments(risk ? "service_risk_inference" : "service_rec_inference", risk ? "deploy_risk_prod" : "deploy_recommendation_prod", 15)), servlet(incident, tool, "read"));
    }

    /** 构造已认证的明确工作空间与事件上下文。 / Construct an authenticated, explicit workspace and incident context. */
    private MockHttpServletRequest servlet(String incident, String tool, String key) {
        var servlet = new MockHttpServletRequest();
        servlet.setAttribute(AgentContract.CONTEXT_ATTRIBUTE,
                new AgentContract.RequestContext("ws_goai_demo", incident, "trace", tool, key, "operator", "request"));
        return servlet;
    }

    /** 构造类型化治理请求，版本及幂等键由调用者明确提供。 / Construct a typed governed request with explicit version and idempotency keys. */
    private <T> AgentContract.ToolRequest<T> request(String tool, String key, String resource, String version, boolean dryRun, T arguments) {
        return new AgentContract.ToolRequest<>("request-" + key, tool, arguments, "approval", "plan", "digest", key,
                resource, 1L, version, "arguments-digest", false, "test", key, dryRun);
    }
}
