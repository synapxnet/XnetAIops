package com.synapxnet.goai.contract;

import java.util.Map;

/**
 * 表示可安全映射到公共工具错误码的契约或治理异常。
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

    /** 获取 HTTP 状态码。 */
    public int getHttpStatus() {
        return httpStatus;
    }

    /** 获取公共错误码。 */
    public String getCode() {
        return code;
    }

    /** 判断调用方是否可重试。 */
    public boolean isRetryable() {
        return retryable;
    }

    /** 获取已脱敏的结构化详情。 */
    public Map<String, Object> getDetails() {
        return details;
    }
}
