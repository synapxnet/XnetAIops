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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 为结构化证据生成确定性的内容摘要和外部证据编号。
 * English: Maintains explicitly configured parsing for trusted evidence and delegated identity contracts.
 */
final class EvidenceDigest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    /** 阻止摘要工具被实例化。 English: Prevents instantiation of the deterministic evidence hash utility. */
    private EvidenceDigest() {
    }

    /**
     * 根据来源、资源版本、观测时间和结构化内容生成证据编号。
     *
     * @param source 证据来源
     * @param data 结构化证据
     * @param resourceVersion 资源版本
     * @param observedAt 观测时间
     * @return 以 ev_ 开头的证据编号
     * English: Calculates the legacy evidence identifier from its source, payload, version and timestamp.
     */
    static String create(String source, Object data, String resourceVersion, Instant observedAt) {
        try {
            String canonical = source + "\n" + resourceVersion + "\n" + observedAt + "\n"
                    + OBJECT_MAPPER.writeValueAsString(data);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String value = HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
            return "ev_" + value.substring(0, 26);
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new AgentContractException(500, "INTERNAL_ERROR", "无法生成证据摘要");
        }
    }
}
