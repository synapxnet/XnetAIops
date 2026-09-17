/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 重启、完整性与故障关闭回归。 Restart, integrity and fail-closed regression.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-15 | Version: 1.3.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopsk8sservice.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.synapxnet.goai.contract.AgentContract;
import com.synapxnet.goai.contract.AgentContractException;
import com.synapxnet.goai.contract.GovernedApprovalVerifier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GovernedCheckpointRecoveryTest {
    private static final String HG2 = "83784d96008415f2738924fe923bb44c2fb81b0b82fa798b844fb5c58ad9308c";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    @TempDir Path temporary;

    /** Windows 测试只替代目录同步；文件 force 与原子 rename 仍执行。 Windows tests replace directory sync only; file force and atomic rename still execute. */
    private static void testDirectorySync(Path directory, String phase) { }

    /** 使用冻结离线推导证据验证全部六域、27版本和72记录，绝不将fixture称为现场导出。 Verifies all six domains, 27 versions and 72 records from frozen offline derivation without presenting the fixture as a live export. */
    @Test
    @EnabledIfSystemProperty(named = "goai.recovery.test-evidence", matches = ".+")
    void preservesFrozenDerivedStateAcrossRepeatedRestarts() throws Exception {
        Path evidence = Path.of(System.getProperty("goai.recovery.test-evidence"));
        byte[] evidenceBytes = Files.readAllBytes(evidence);
        assertEquals("7db8531784c2fe4febbe3d2d38c121ffe5afaa4ab0f49be04b05c0a6c749de27", digest(evidenceBytes));
        JsonNode wrapper = MAPPER.readTree(evidenceBytes);
        assertFalse(wrapper.path("importable").asBoolean(true));
        JsonNode expected = wrapper.required("derivedState");
        assertEquals(6, expected.path("domainState").size());
        assertEquals(27, expected.path("tracker").path("liveVersions").size());
        assertEquals(72, expected.path("tracker").path("executions").size());
        var fixture = fixture(HG2, false);
        ObjectNode document = (ObjectNode) MAPPER.readTree(Files.readAllBytes(fixture.bootstrap()));
        document.set("tracker", expected.required("tracker"));
        document.set("domainState", expected.required("domainState"));
        document.putObject("metadata").put("fixtureOnly", true).put("evidenceSha256", digest(evidenceBytes));
        document.putObject("derivation").put("method", "OFFLINE_RECONSTRUCTION_TEST_FIXTURE").put("liveJvmExport", false);
        byte[] bytes = MAPPER.writeValueAsBytes(document);
        Files.write(fixture.bootstrap(), bytes);
        Fixture actual = new Fixture(fixture.directory(), fixture.bootstrap(), digest(bytes));
        try (var controller = open(actual)) {
            assertEquals(expected, state(controller));
            assertEquals(expected, diskState(actual));
        }
        Files.delete(actual.bootstrap());
        byte[] firstCheckpoint = Files.readAllBytes(actual.directory().resolve("checkpoint.json"));
        for (int restart = 0; restart < 3; restart++) {
            try (var controller = open(actual)) {
                assertEquals(expected, state(controller));
                assertEquals(expected, diskState(actual));
                assertEquals("46", metrics(controller).meta().resourceVersion());
                assertEquals(expected, state(controller));
                assertArrayEquals(firstCheckpoint, Files.readAllBytes(actual.directory().resolve("checkpoint.json")));
            }
        }
        assertEquals(digest(evidenceBytes), digest(Files.readAllBytes(evidence)));
    }

    /** 正式候选bootstrap逐字节校验后完整保全且可重复重启，保留其离线重建声明。 Preserves the exact reviewed candidate bootstrap across repeated restarts while retaining its offline-reconstruction declaration. */
    @Test
    @EnabledIfSystemProperty(named = "goai.recovery.test-bootstrap", matches = ".+")
    void importsReviewedRecoveryBootstrapWithoutLosingState() throws Exception {
        Path bootstrap = Path.of(System.getProperty("goai.recovery.test-bootstrap"));
        byte[] sourceBytes = Files.readAllBytes(bootstrap);
        String sourceDigest = "9cbcb3234fee65ff3fa71f0911925482bcb37a4bfc35110bf147fde087e43156";
        assertEquals(sourceDigest, digest(sourceBytes));
        JsonNode document = MAPPER.readTree(sourceBytes);
        assertFalse(document.required("reconstruction").path("isFinalLiveJvmExport").asBoolean(true));
        ObjectNode expected = MAPPER.createObjectNode();
        expected.set("tracker", document.required("tracker"));
        expected.set("domainState", document.required("domainState"));
        assertEquals(6, expected.path("domainState").size());
        assertEquals(27, expected.path("tracker").path("liveVersions").size());
        assertEquals(72, expected.path("tracker").path("executions").size());
        Path directory = Files.createDirectory(temporary.resolve("reviewed-state"));
        Fixture fixture = new Fixture(directory, bootstrap, sourceDigest);
        byte[] firstCheckpoint = null;
        for (int restart = 0; restart < 4; restart++) {
            try (var controller = open(fixture)) {
                assertEquals(expected, state(controller));
                assertEquals(expected, diskState(fixture));
                assertEquals("46", metrics(controller).meta().resourceVersion());
                assertEquals(expected, state(controller));
                byte[] current = Files.readAllBytes(directory.resolve("checkpoint.json"));
                if (firstCheckpoint == null) firstCheckpoint = current;
                else assertArrayEquals(firstCheckpoint, current);
            }
        }
        assertEquals(sourceDigest, digest(Files.readAllBytes(bootstrap)));
        assertFalse(Files.exists(bootstrap.resolveSibling(bootstrap.getFileName() + ".consumed")));
    }

    /** 精确 hg2 快照只消费一次，之后无需原始文件即可重复启动。 Initializes an exact hg2 snapshot once and restarts repeatedly without the source file. */
    @Test
    void restartsWithoutReplayingBootstrap() throws Exception {
        var fixture = fixture(HG2, true);
        JsonNode expected;
        try (var controller = open(fixture)) {
            tune(controller, "inc_new", "next", "42", false);
            expected = state(controller);
        }
        Files.delete(fixture.bootstrap());
        for (int i = 0; i < 3; i++) {
            try (var controller = open(fixture)) {
                assertEquals(expected, state(controller));
                assertEquals("43", metrics(controller).meta().resourceVersion());
            }
        }
    }

    /** 首次读取初始化的领域与全部版本必须共同落盘。 Persists domains and every version initialized by the first read together. */
    @Test
    void persistsReadInitialization() throws Exception {
        var fixture = fixture(HG2, false);
        try (var controller = open(fixture)) {
            assertEquals("42", metrics(controller).meta().resourceVersion());
            assertEquals(5, diskState(fixture).path("tracker").path("liveVersions").size());
            assertEquals(1, diskState(fixture).path("domainState").size());
        }
        try (var restored = open(fixture)) { assertEquals("42", metrics(restored).meta().resourceVersion()); }
    }

    /** 演练版本和执行历史在重启后保留，真实领域不被演练改变。 Retains rehearsal versions and history across restart without changing the live domain. */
    @Test
    void persistsDryRunSeparately() throws Exception {
        var fixture = fixture(HG2, true);
        try (var controller = open(fixture)) {
            tune(controller, "inc_dry", "dry-one", "42", true);
            tune(controller, "inc_dry", "dry-two", "43", true);
        }
        try (var controller = open(fixture)) {
            assertEquals("42", metrics(controller).meta().resourceVersion());
            assertEquals("BASELINE", diskState(fixture).path("domainState").path("ws_goai_demo:service_rec_inference").path("runtimeProfile").asText());
            assertEquals(2, diskState(fixture).path("tracker").path("executions").size());
            assertEquals(1, diskState(fixture).path("tracker").path("rehearsalVersions").size());
            tune(controller, "inc_dry", "dry-two", "43", true);
            assertEquals(2, diskState(fixture).path("tracker").path("executions").size());
        }
    }

    /** 返回成功前磁盘已包含完整执行，重启幂等回放不会重复变更。 Ensures complete execution is on disk before success and replay never repeats the mutation after restart. */
    @Test
    void commitsBeforeSuccessAndReplays() throws Exception {
        var fixture = fixture(HG2, true);
        Map<String, Object> first;
        try (var controller = open(fixture)) {
            first = tune(controller, "inc_live", "once", "42", false).data();
            assertEquals(1, diskState(fixture).path("tracker").path("executions").size());
            assertEquals(43, diskState(fixture).path("tracker").path("liveVersions").path("ws_goai_demo:deploy_recommendation_prod/runtime").asLong());
        }
        try (var controller = open(fixture)) {
            assertEquals(first, tune(controller, "inc_live", "once", "42", false).data());
            assertEquals("43", metrics(controller).meta().resourceVersion());
        }
    }

    /** 同幂等键的并发请求只能产生一条持久化执行。 Concurrent requests sharing an idempotency key create only one persisted execution. */
    @Test
    void serializesConcurrentRequests() throws Exception {
        var fixture = fixture(HG2, true);
        try (var controller = open(fixture)) {
            var executor = Executors.newFixedThreadPool(4);
            try {
                var tasks = java.util.stream.IntStream.range(0, 8).mapToObj(index -> (java.util.concurrent.Callable<String>) () ->
                        tune(controller, "inc_concurrent", "same", "42", false).meta().resourceVersion()).toList();
                for (var result : executor.invokeAll(tasks)) assertEquals("43", result.get());
            } finally { executor.shutdownNow(); }
            assertEquals(1, diskState(fixture).path("tracker").path("executions").size());
        }
    }

    /** 同一目录只能由一个实例持有。 Only one instance may own a state directory. */
    @Test
    void rejectsConcurrentDirectoryOwner() throws Exception {
        var fixture = fixture(HG2, true);
        try (var controller = open(fixture)) { assertThrows(IllegalStateException.class, () -> open(fixture)); }
        try (var controller = open(fixture)) { assertEquals("42", metrics(controller).meta().resourceVersion()); }
    }

    /** 删除或损坏状态时拒绝启动，不以仍存在的bootstrap重新初始化。 Rejects missing or corrupted state without reinitializing from a retained bootstrap. */
    @ParameterizedTest
    @ValueSource(strings = {"checkpoint.json", "initialized.json", "both", "corrupt-checkpoint", "corrupt-marker"})
    void rejectsMissingOrCorruptState(String mode) throws Exception {
        var fixture = fixture(HG2, true);
        try (var ignored = open(fixture)) { }
        if (mode.startsWith("corrupt")) Files.writeString(fixture.directory().resolve(mode.endsWith("checkpoint") ? "checkpoint.json" : "initialized.json"), "{}");
        else if (mode.equals("both")) { Files.delete(fixture.directory().resolve("checkpoint.json")); Files.delete(fixture.directory().resolve("initialized.json")); }
        else Files.delete(fixture.directory().resolve(mode));
        assertThrows(IllegalStateException.class, () -> open(fixture));
        assertTrue(Files.exists(fixture.bootstrap()));
    }

    /** 仅接受固定 hg2 来源，旧快照不能被重新消费。 Accepts only the pinned hg2 source and never reconsumes an old snapshot. */
    @ParameterizedTest
    @ValueSource(strings = {"48325d55b67d06c9e9508b340c24a5839dc352f1d8c33c07a22707c3b48b7394", "65d376a6f561d42360c5fdff002c8a6625c2fe560507bac6f8fe875b7b94e3c6", "unrecognized"})
    void rejectsOtherSourceArtifacts(String source) throws Exception {
        var fixture = fixture(source, true);
        assertThrows(IllegalStateException.class, () -> open(fixture));
    }

    /** 原 consumed 标记存在时拒绝初始化并保留字节。 Rejects a consumed bootstrap while preserving the existing marker bytes. */
    @Test
    void preservesConsumedMarker() throws Exception {
        var fixture = fixture(HG2, true);
        Path consumed = fixture.bootstrap().resolveSibling(fixture.bootstrap().getFileName() + ".consumed");
        Files.writeString(consumed, "preserve");
        assertThrows(IllegalStateException.class, () -> open(fixture));
        assertEquals("preserve", Files.readString(consumed));
    }

    /** rename 前后故障均返回失败并锁死本实例；重启根据已落盘幂等结果继续。 Faults before or after rename fail the request and poison the instance; restart uses whichever idempotent result is persisted. */
    @ParameterizedTest
    @ValueSource(strings = {"before-replace", "after-replace"})
    void failsClosedAtCommitBoundaries(String phase) throws Exception {
        var fixture = fixture(HG2, true);
        AtomicReference<String> failure = new AtomicReference<>();
        try (var controller = new CompetitionInferenceToolController(verifier(), fixture.directory(), fixture.bootstrap(), fixture.digest(),
                (directory, currentPhase) -> { if (currentPhase.equals(failure.get())) { failure.set(null); throw new IOException("injected"); } })) {
            failure.set(phase);
            assertThrows(AgentContractException.class, () -> tune(controller, "inc_fault", "once", "42", false));
            assertThrows(AgentContractException.class, () -> metrics(controller));
        }
        try (var controller = open(fixture)) {
            assertEquals(phase.equals("after-replace") ? "43" : "42", metrics(controller).meta().resourceVersion());
            tune(controller, "inc_fault", "once", "42", false);
            assertEquals("43", metrics(controller).meta().resourceVersion());
            assertEquals(1, diskState(fixture).path("tracker").path("executions").size());
        }
    }

    /** 运行时磁盘状态丢失必须阻断读取与执行。 Missing on-disk state at runtime blocks both reads and execution. */
    @Test
    void rejectsRuntimeCheckpointDeletion() throws Exception {
        var fixture = fixture(HG2, true);
        try (var controller = open(fixture)) {
            Files.delete(fixture.directory().resolve("checkpoint.json"));
            assertThrows(AgentContractException.class, () -> metrics(controller));
            assertThrows(AgentContractException.class, () -> tune(controller, "inc_missing", "once", "42", false));
        }
    }

    /** 初始化标记已落盘但checkpoint未完成时不能自动重放。 Never replays automatically when initialization was marked but the checkpoint did not complete. */
    @Test
    void rejectsInterruptedInitialization() throws Exception {
        var fixture = fixture(HG2, true);
        assertThrows(IllegalStateException.class, () -> new CompetitionInferenceToolController(verifier(), fixture.directory(), fixture.bootstrap(), fixture.digest(),
                (directory, phase) -> { if (phase.equals("after-replace")) throw new IOException("initial marker committed"); }));
        assertTrue(Files.exists(fixture.directory().resolve("initialized.json")));
        assertFalse(Files.exists(fixture.directory().resolve("checkpoint.json")));
        assertThrows(IllegalStateException.class, () -> open(fixture));
    }

    /** 领域覆盖缺失时拒绝看似完整的版本快照。 Rejects a version snapshot whose corresponding domain state is missing. */
    @Test
    void rejectsUnboundDomainVersions() throws Exception {
        var fixture = fixture(HG2, true);
        ObjectNode document = (ObjectNode) MAPPER.readTree(Files.readAllBytes(fixture.bootstrap()));
        document.set("domainState", MAPPER.createObjectNode());
        byte[] bytes = MAPPER.writeValueAsBytes(document);
        Files.write(fixture.bootstrap(), bytes);
        var changed = new Fixture(fixture.directory(), fixture.bootstrap(), digest(bytes));
        assertThrows(IllegalStateException.class, () -> open(changed));
    }

    /** 文件内容与输入摘要不符时拒绝首次初始化。 Rejects first initialization when bootstrap bytes do not match the supplied digest. */
    @Test
    void rejectsBootstrapDigestMismatch() throws Exception {
        var fixture = fixture(HG2, true);
        Files.writeString(fixture.bootstrap(), Files.readString(fixture.bootstrap()) + " ");
        assertThrows(IllegalStateException.class, () -> open(fixture));
        assertFalse(Files.exists(fixture.directory().resolve("initialized.json")));
        assertFalse(Files.exists(fixture.directory().resolve("checkpoint.json")));
    }

    /** 重启必须保留初始绑定，不能将另一个摘要挂到现有状态上。 Requires the original binding on restart rather than attaching another digest to existing state. */
    @Test
    void rejectsChangedBootstrapBinding() throws Exception {
        var fixture = fixture(HG2, true);
        try (var ignored = open(fixture)) { }
        var changed = new Fixture(fixture.directory(), fixture.bootstrap(), "f".repeat(64));
        assertThrows(IllegalStateException.class, () -> open(changed));
        try (var controller = open(fixture)) { assertEquals("42", metrics(controller).meta().resourceVersion()); }
    }

    /** 可解析但摘要不符的状态不能恢复，运行时标记篡改也会熔断。 Rejects parseable payload tampering and poisons requests after runtime marker tampering. */
    @Test
    void rejectsPayloadAndRuntimeMarkerTampering() throws Exception {
        var fixture = fixture(HG2, true);
        try (var ignored = open(fixture)) { }
        Path checkpoint = fixture.directory().resolve("checkpoint.json");
        ObjectNode record = (ObjectNode) MAPPER.readTree(Files.readAllBytes(checkpoint));
        ((ObjectNode) record.path("payload").path("domainState").path("ws_goai_demo:service_rec_inference")).put("gpuNodes", 100);
        Files.write(checkpoint, MAPPER.writeValueAsBytes(record));
        assertThrows(IllegalStateException.class, () -> open(fixture));
        var other = fixture(HG2, true);
        try (var controller = open(other)) {
            Files.writeString(other.directory().resolve("initialized.json"), "{}");
            assertThrows(AgentContractException.class, () -> metrics(controller));
            assertThrows(AgentContractException.class, () -> tune(controller, "inc_marker", "once", "42", false));
        }
    }

    /** 创建完整实际Controller格式的离线bootstrap。 Creates an offline bootstrap using the actual controller's complete state format. */
    private Fixture fixture(String source, boolean initializeRead) throws Exception {
        Path directory = Files.createDirectory(temporary.resolve("state-" + java.util.UUID.randomUUID()));
        Path bootstrap = temporary.resolve("bootstrap-" + java.util.UUID.randomUUID() + ".json");
        var baseline = new CompetitionInferenceToolController(verifier());
        if (initializeRead) metrics(baseline);
        JsonNode state = state(baseline);
        ObjectNode document = MAPPER.createObjectNode();
        document.put("schema", "openxnet.governed-state-export.v1"); document.put("platform", "aiops");
        document.put("controller", CompetitionInferenceToolController.class.getName()); document.put("sourceJarSha256", source);
        document.put("domainField", "incidentStates"); document.put("readOnly", true); document.put("exportedAt", "2026-09-15T00:00:00Z");
        document.set("tracker", state.get("tracker")); document.set("domainState", state.get("domainState"));
        byte[] bytes = MAPPER.writeValueAsBytes(document);
        Files.write(bootstrap, bytes);
        return new Fixture(directory, bootstrap, digest(bytes));
    }

    /** 打开真实Controller，仅替代外部审批与Windows目录同步。 Opens the real controller while replacing only external approval and Windows directory sync. */
    private CompetitionInferenceToolController open(Fixture fixture) {
        return new CompetitionInferenceToolController(verifier(), fixture.directory(), fixture.bootstrap(), fixture.digest(), GovernedCheckpointRecoveryTest::testDirectorySync);
    }

    /** 读取实际完整状态并按JSON整数语义归一化，逐键比较而不依赖Java节点宽度。 Reads complete actual state and normalizes JSON integer representation for exact key/value comparison independent of Java node width. */
    private JsonNode state(CompetitionInferenceToolController controller) throws Exception {
        var method = CompetitionInferenceToolController.class.getDeclaredMethod("completeState");
        method.setAccessible(true);
        return MAPPER.readTree(MAPPER.writeValueAsBytes(method.invoke(controller)));
    }

    /** 从磁盘文件读取已提交状态，不能用内存替代落盘证明。 Reads committed state from disk rather than substituting an in-memory assertion. */
    private JsonNode diskState(Fixture fixture) throws IOException {
        return MAPPER.readTree(Files.readAllBytes(fixture.directory().resolve("checkpoint.json"))).required("payload");
    }

    /** 使用实际审批接口形状，但不访问网络。 Uses the real approval interface shape without network access. */
    private GovernedApprovalVerifier verifier() {
        var verifier = mock(GovernedApprovalVerifier.class);
        when(verifier.verify(any(), any())).thenReturn(new GovernedApprovalVerifier.ApprovalDecision("approval", "approver", "executor", "arguments-digest"));
        return verifier;
    }

    /** 执行真实领域内存调优与版本逻辑。 Executes actual in-memory domain tuning and version logic. */
    private AgentContract.ToolResponse<Map<String, Object>> tune(CompetitionInferenceToolController controller, String incident, String key, String version, boolean dryRun) {
        String tool = "aiops.inference.runtime.tune";
        return controller.tuneRuntime(request(tool, key, "deploy_recommendation_prod/runtime", version, dryRun,
                new CompetitionInferenceToolController.RuntimeTuneArguments("deploy_recommendation_prod", 16, 500, "DYNAMIC_SHAPE_OPTIMIZED")), servlet(incident, tool, key));
    }

    /** 读取真实Controller指标包络。 Reads the actual controller's metrics envelope. */
    private AgentContract.ToolResponse<Map<String, Object>> metrics(CompetitionInferenceToolController controller) {
        String tool = "aiops.inference.metrics.get";
        return controller.metrics(request(tool, "read", null, null, false,
                new CompetitionInferenceToolController.InferenceMetricsArguments("service_rec_inference", "deploy_recommendation_prod", 15)), servlet("inc_read", tool, "read"));
    }

    /** 生成已鉴权上下文替身。 Creates an authenticated-context test fixture. */
    private MockHttpServletRequest servlet(String incident, String tool, String key) {
        var servlet = new MockHttpServletRequest();
        servlet.setAttribute(AgentContract.CONTEXT_ATTRIBUTE, new AgentContract.RequestContext("ws_goai_demo", incident, "trace", tool, key, "operator", "request"));
        return servlet;
    }

    /** 构造类型化审批请求。 Constructs the typed governed request. */
    private <T> AgentContract.ToolRequest<T> request(String tool, String key, String resource, String version, boolean dryRun, T arguments) {
        return new AgentContract.ToolRequest<>("request-" + key, tool, arguments, "approval", "plan", "digest", key, resource, 1L, version, "arguments-digest", false, "test", key, dryRun);
    }

    /** 对测试bootstrap计算实际SHA-256。 Computes the actual test bootstrap SHA-256. */
    private String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** 保存离线目录、bootstrap与实际摘要。 Holds the offline directory, bootstrap and actual digest. */
    private record Fixture(Path directory, Path bootstrap, String digest) { }
}
