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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 验证 OpenXnet Gateway 签发的短期、受众受限 HS256 委托令牌。
 * English: Maintains explicitly configured parsing for trusted evidence and delegated identity contracts.
 */
final class DelegatedTokenVerifier {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final byte[] secret;
    private final String audience;

    /**
     * 创建委托令牌验证器；未配置密钥时保持失败关闭。
     *
     * @param secret 委托令牌共享密钥，必须由运行环境注入
     * @param audience 当前 Adapter 的受众
     * English: Configures the trusted delegation secret and audience without creating default credentials.
     */
    DelegatedTokenVerifier(String secret, String audience) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        this.audience = audience;
    }

    /**
     * 验证签名、过期时间、受众、Workspace 和工具授权并返回主体。
     *
     * @param authorization Authorization Header
     * @param workspaceId 目标 Workspace
     * @param toolName 目标工具名
     * @return 可信 actorId
     * English: Validates the bounded delegated token and returns its verified actor identity.
     */
    String verify(String authorization, String workspaceId, String toolName) {
        if (secret.length < 32) {
            throw new AgentContractException(503, "UPSTREAM_UNAVAILABLE", "Agent 委托令牌验证器未配置");
        }
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AgentContractException(401, "UNAUTHENTICATED", "缺少 Bearer 委托令牌");
        }
        String token = authorization.substring(7).trim();
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw new AgentContractException(401, "UNAUTHENTICATED", "委托令牌格式无效");
        }
        try {
            Map<String, Object> header = decode(parts[0]);
            if (!"HS256".equals(header.get("alg"))) {
                throw new AgentContractException(401, "UNAUTHENTICATED", "委托令牌算法无效");
            }
            byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
            byte[] expected = sign(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new AgentContractException(401, "UNAUTHENTICATED", "委托令牌签名无效");
            }
            Map<String, Object> claims = decode(parts[1]);
            validateClaims(claims, workspaceId, toolName);
            Object subject = claims.get("sub");
            if (!(subject instanceof String actorId) || actorId.isBlank()) {
                throw new AgentContractException(401, "UNAUTHENTICATED", "委托令牌缺少主体");
            }
            return actorId;
        } catch (AgentContractException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AgentContractException(401, "UNAUTHENTICATED", "委托令牌校验失败");
        }
    }

    /**
     * 解码 JWT JSON 段。
     *
     * @param value Base64URL 编码段
     * @return JSON 对象
     * English: Decodes one Base64URL JSON token segment.
     */
    private Map<String, Object> decode(String value) throws Exception {
        byte[] decoded = Base64.getUrlDecoder().decode(value);
        return OBJECT_MAPPER.readValue(decoded, new TypeReference<>() { });
    }

    /**
     * 使用 HMAC-SHA256 计算 JWT 签名。
     *
     * @param input Header 与 Payload 拼接值
     * @return 原始签名字节
     * English: Calculates the expected HMAC-SHA256 token signature.
     */
    private byte[] sign(String input) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 校验委托令牌的时效、受众、Workspace 和工具范围。
     *
     * @param claims 已验证签名的声明
     * @param workspaceId 请求 Workspace
     * @param toolName 请求工具
     * English: Rejects expired tokens and mismatched audience, workspace or tool scopes.
     */
    private void validateClaims(Map<String, Object> claims, String workspaceId, String toolName) {
        long now = Instant.now().getEpochSecond();
        Object expires = claims.get("exp");
        if (!(expires instanceof Number number) || number.longValue() <= now) {
            throw new AgentContractException(401, "UNAUTHENTICATED", "委托令牌已过期");
        }
        if (!audience.equals(claims.get("aud"))) {
            throw new AgentContractException(401, "UNAUTHENTICATED", "委托令牌受众无效");
        }
        if (!workspaceId.equals(claims.get("workspace_id"))) {
            throw new AgentContractException(403, "PERMISSION_DENIED", "委托令牌无权访问该 Workspace");
        }
        Object tools = claims.get("tools");
        if (!(tools instanceof List<?> values) || !values.contains(toolName)) {
            throw new AgentContractException(403, "PERMISSION_DENIED", "委托令牌无权调用该工具");
        }
    }
}
