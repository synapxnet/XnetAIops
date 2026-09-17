/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 无网络持久化文件系统探针。 Network-free persistence filesystem probe.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-15 | Version: 1.3.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopsk8sservice.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Map;

public final class GovernedCheckpointFileProbe {
    /** 禁止实例化离线探针。 Prevents instantiation of the offline probe. */
    private GovernedCheckpointFileProbe() { }

    /** 仅保存明确的探针数据；不启动 Spring，不读取模型或业务配置。 Saves explicit probe data only without starting Spring or reading model or business configuration. */
    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !java.util.Set.of("initialize", "advance", "verify").contains(args[1])) {
            throw new IllegalArgumentException("Usage: <absolute-probe-directory> initialize|advance|verify");
        }
        ObjectMapper mapper = new ObjectMapper();
        try (var store = new GovernedStateCheckpointStore(Path.of(args[0]), "f".repeat(64))) {
            if ("initialize".equals(args[1])) {
                store.initialize(mapper.valueToTree(Map.of("probeOnly", true, "revision", 1, "idempotency", "offline-probe")));
            } else {
                var payload = store.load();
                if (!payload.path("probeOnly").asBoolean()) throw new IllegalStateException("PROBE_DATA_REQUIRED");
                if ("advance".equals(args[1])) store.commit(mapper.valueToTree(Map.of("probeOnly", true, "revision", 2, "idempotency", "offline-probe")));
            }
            var result = store.load();
            System.out.println(mapper.writeValueAsString(Map.of("status", "FILESYSTEM_PROBE_ONLY", "revision", result.path("revision").asInt(), "networkUsed", false)));
        }
    }
}
