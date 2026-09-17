/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 运行证据与受限身份对接。 Operations evidence and scoped identity integration.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-13
 * Version: 1.0.0 | Security Level: INTERNAL
 * Maintainer: maoyo | Email: synapxnet@gmail.com
 * Restored selectively from the SynapXnet competition baseline b260b27.
 */
package com.synapxnet.goai.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证委托 Token 的签名、受众、Workspace、工具和时效边界。
 * English: Maintains explicitly configured parsing for trusted evidence and delegated identity contracts.
 */
class DelegatedTokenVerifierTest {

    private static final String SECRET = "goai-delegation-secret-for-tests-123456789";
    private static final String AUDIENCE = "openxnet-agent-adapter";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 合法的单 Workspace、单工具 Token 应返回可信主体。 English: Provides the scoped accepts bounded token contract and preserves structured error handling. */
    /** 验证受限身份与错误响应契约。 English: Verifies accepts bounded token. English: Provides the scoped accepts bounded token contract and preserves structured error handling. */
    @Test
    void acceptsBoundedToken() throws Exception {
        DelegatedTokenVerifier verifier = new DelegatedTokenVerifier(SECRET, AUDIENCE);

        String actor = verifier.verify(
                "Bearer " + token(AUDIENCE, "ws_goai_demo", List.of("aiops.alert.get"), 60),
                "ws_goai_demo",
                "aiops.alert.get");

        assertEquals("operator", actor);
    }

    /** 错误 Workspace、工具、受众和过期 Token 必须失败关闭。 English: Provides the scoped rejects cross boundary tokens contract and preserves structured error handling. */
    /** 验证受限身份与错误响应契约。 English: Verifies rejects cross boundary tokens. English: Provides the scoped rejects cross boundary tokens contract and preserves structured error handling. */
    @Test
    void rejectsCrossBoundaryTokens() throws Exception {
        DelegatedTokenVerifier verifier = new DelegatedTokenVerifier(SECRET, AUDIENCE);
        String valid = "Bearer " + token(AUDIENCE, "ws_goai_demo", List.of("aiops.alert.get"), 60);

        assertEquals("PERMISSION_DENIED", assertThrows(
                AgentContractException.class,
                () -> verifier.verify(valid, "ws_other", "aiops.alert.get")).getCode());
        assertEquals("PERMISSION_DENIED", assertThrows(
                AgentContractException.class,
                () -> verifier.verify(valid, "ws_goai_demo", "aiops.service.health")).getCode());
        assertEquals("UNAUTHENTICATED", assertThrows(
                AgentContractException.class,
                () -> verifier.verify(
                        "Bearer " + token("other-adapter", "ws_goai_demo", List.of("aiops.alert.get"), 60),
                        "ws_goai_demo", "aiops.alert.get")).getCode());
        assertEquals("UNAUTHENTICATED", assertThrows(
                AgentContractException.class,
                () -> verifier.verify(
                        "Bearer " + token(AUDIENCE, "ws_goai_demo", List.of("aiops.alert.get"), -1),
                        "ws_goai_demo", "aiops.alert.get")).getCode());
    }

    /** 生成仅用于单元测试的 HS256 JWT；输入边界声明，返回紧凑 Token。 English: Provides the scoped token contract and preserves structured error handling. */
    private String token(
            String audience,
            String workspaceId,
            List<String> tools,
            long lifetimeSeconds) throws Exception {
        String header = encode(Map.of("alg", "HS256", "typ", "JWT"));
        String payload = encode(Map.of(
                "sub", "operator",
                "workspace_id", workspaceId,
                "aud", audience,
                "tools", tools,
                "exp", Instant.now().getEpochSecond() + lifetimeSeconds));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal((header + "." + payload).getBytes(StandardCharsets.UTF_8)));
        return header + "." + payload + "." + signature;
    }

    /** 把测试声明编码为 Base64URL JSON 段。 English: Provides the scoped encode contract and preserves structured error handling. */
    private String encode(Map<String, Object> value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                OBJECT_MAPPER.writeValueAsBytes(value));
    }
}
