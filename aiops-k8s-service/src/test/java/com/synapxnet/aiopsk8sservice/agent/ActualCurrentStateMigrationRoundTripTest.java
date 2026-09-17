/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 当前真实导出离线导入核验 / Offline import verification of the actual current export.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-15
 * Version: 1.0.0 | Security Level: INTERNAL
 * __version__: 1.0.0 | __author__: maoyo | __copyright__: Copyright 2026 Synapxnet
 * __maintainer__: maoyo | __email__: synapxnet@gmail.com
 */
package com.synapxnet.aiopsk8sservice.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.synapxnet.goai.contract.GovernedApprovalVerifier;
import com.synapxnet.goai.contract.GovernedResourceVersionTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 使用显式真实导出，在隔离实例核验无损恢复。 / Verify lossless restoration in an isolated instance using an explicit actual export. */
@EnabledIfSystemProperty(named = "goai.migration.actual-source", matches = ".+")
class ActualCurrentStateMigrationRoundTripTest {
    @TempDir Path temporary;

    /** 真实导出的所有状态原值恢复，重复消费拒绝且原文件不变。 / Restore every actual exported value, reject duplicate consumption and preserve the source file. */
    @Test
    void importsActualCurrentExportWithoutChangingSource() throws Exception {
        Path source = Path.of(System.getProperty("goai.migration.actual-source")).toAbsolutePath();
        String expectedDigest = System.getProperty("goai.migration.actual-sha256", "");
        assertTrue(expectedDigest.matches("[a-f0-9]{64}"), "Explicit actual source digest required");
        for (Path parent = source; parent != null; parent = parent.getParent()) {
            assertFalse(Files.isSymbolicLink(parent), "Actual source path must not contain links");
        }
        assertTrue(Files.isRegularFile(source) && Files.size(source) <= 4 * 1024 * 1024);
        byte[] bytes = Files.readAllBytes(source);
        assertEquals(expectedDigest, digest(bytes));
        var mapper = new ObjectMapper();
        var document = mapper.readTree(bytes);
        assertEquals("openxnet.governed-state-export.v1", document.path("schema").asText());
        assertEquals("65d376a6f561d42360c5fdff002c8a6625c2fe560507bac6f8fe875b7b94e3c6", document.path("sourceJarSha256").asText());
        Path copy = temporary.resolve("actual-current.json");
        Files.write(copy, bytes, java.nio.file.StandardOpenOption.CREATE_NEW);
        try {
            var controller = new CompetitionInferenceToolController(mock(GovernedApprovalVerifier.class), copy.toString(), expectedDigest);
            var trackerField = CompetitionInferenceToolController.class.getDeclaredField("versionTracker");
            trackerField.setAccessible(true);
            var tracker = (GovernedResourceVersionTracker) trackerField.get(controller);
            // 按原导出JSON表示比较，避免IntNode与LongNode的内存类型差异。 / Compare the exported JSON representation without an IntNode versus LongNode memory-type mismatch.
            var restoredTracker = mapper.readTree(mapper.writeValueAsBytes(tracker.snapshot()));
            assertTrue(document.get("tracker").equals(restoredTracker), "Actual tracker JSON must round-trip exactly");
            var domainsField = CompetitionInferenceToolController.class.getDeclaredField("incidentStates");
            domainsField.setAccessible(true);
            var domains = (Map<?, ?>) domainsField.get(controller);
            ObjectNode actualDomains = mapper.createObjectNode();
            for (var item : domains.entrySet()) {
                ObjectNode state = actualDomains.putObject((String) item.getKey());
                for (String name : List.of("gpuNodes", "replicas", "batchSize", "trafficPercent", "runtimeProfile", "autoscalingReady", "converged", "initiallyStable")) {
                    var field = item.getValue().getClass().getDeclaredField(name);
                    field.setAccessible(true);
                    state.set(name, mapper.valueToTree(field.get(item.getValue())));
                }
            }
            assertTrue(document.get("domainState").equals(actualDomains), "Actual domain values must round-trip exactly");
            assertTrue(Files.isRegularFile(temporary.resolve("actual-current.json.consumed")));
            assertThrows(IllegalStateException.class,
                    () -> new CompetitionInferenceToolController(mock(GovernedApprovalVerifier.class), copy.toString(), expectedDigest));
        } finally {
            assertEquals(expectedDigest, digest(Files.readAllBytes(source)));
            assertFalse(Files.exists(source.resolveSibling(source.getFileName() + ".consumed")), "The original export must remain unconsumed");
        }
    }

    /** 计算真实输入原字节摘要。 / Compute the digest of the actual input bytes. */
    private String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
