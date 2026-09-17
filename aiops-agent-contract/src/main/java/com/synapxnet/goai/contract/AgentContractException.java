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

import java.util.Map;

/**
 * 表示可安全映射到公共工具错误码的契约或治理异常。
 * English: Creates a public contract failure with an explicit HTTP status and bounded error details.
 */
public class AgentContractException extends RuntimeException {

    private final int httpStatus;
    private final String code;
    private final boolean retryable;
    private final Map<String, Object> details;

    /**
     * 创建不可重试且不携带详情的公共异常。
     *
     * @param httpStatus HTTP 状态码
     * @param code 公共错误码
     * @param message 可向调用方展示的脱敏消息
     * English: Creates a public contract failure with an explicit HTTP status and bounded error details.
     */
    public AgentContractException(int httpStatus, String code, String message) {
        this(httpStatus, code, message, false, Map.of());
    }

    /**
     * 创建包含安全详情的公共异常。
     *
     * @param httpStatus HTTP 状态码
     * @param code 公共错误码
     * @param message 可向调用方展示的脱敏消息
     * @param retryable 是否允许受控重试
     * @param details 不含凭据和堆栈的错误详情
     * English: Creates a public contract failure with an explicit HTTP status and bounded error details.
     */
    public AgentContractException(
            int httpStatus,
            String code,
            String message,
            boolean retryable,
            Map<String, Object> details) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
        this.retryable = retryable;
        this.details = Map.copyOf(details);
    }

    /** 获取 HTTP 状态码。 English: Returns the HTTP status for this structured domain error. */
    public int getHttpStatus() {
        return httpStatus;
    }

    /** 获取公共错误码。 English: Returns the stable machine-readable error code. */
    public String getCode() {
        return code;
    }

    /** 判断调用方是否可重试。 English: Reports whether the caller may retry this failure. */
    public boolean isRetryable() {
        return retryable;
    }

    /** 获取已脱敏的结构化详情。 English: Returns the bounded structured failure details. */
    public Map<String, Object> getDetails() {
        return details;
    }
}
