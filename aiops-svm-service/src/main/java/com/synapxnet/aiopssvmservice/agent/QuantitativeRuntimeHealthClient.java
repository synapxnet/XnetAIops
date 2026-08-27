package com.synapxnet.aiopssvmservice.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapxnet.goai.contract.AgentContractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 从隔离后端网络读取量化研究运行时健康状态。
 */
@Component
public class QuantitativeRuntimeHealthClient {

    private static final String SERVICE_UID = "service_quant_signal";

    private final URI healthUri;
    private final String serviceToken;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * 创建量化运行时健康客户端并固定连接超时。
     *
     * @param runtimeUrl 内部运行时基地址
     * @param serviceToken 内部服务令牌
     * @param objectMapper JSON 解析器
     */
    @Autowired
    public QuantitativeRuntimeHealthClient(
            @Value("${xnet.quantitative.runtime-url}") String runtimeUrl,
            @Value("${xnet.quantitative.runtime-token}") String serviceToken,
            ObjectMapper objectMapper) {
        this(
                runtimeUrl,
                serviceToken,
                objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    /**
     * 创建可注入 HTTP 客户端的实例，供单元测试复用。
     *
     * @param runtimeUrl 内部运行时基地址
     * @param serviceToken 内部服务令牌
     * @param objectMapper JSON 解析器
     * @param httpClient Java HTTP 客户端
     */
    QuantitativeRuntimeHealthClient(
            String runtimeUrl,
            String serviceToken,
            ObjectMapper objectMapper,
            HttpClient httpClient) {
        URI baseUri = validateBaseUri(runtimeUrl);
        this.healthUri = baseUri.resolve("/health");
        this.serviceToken = serviceToken == null ? "" : serviceToken.trim();
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    /**
     * 判断服务标识是否属于量化研究运行时。
     *
     * @param serviceUid AIOps 服务标识
     * @return 属于量化信号服务时返回 true
     */
    public boolean supports(String serviceUid) {
        return SERVICE_UID.equals(serviceUid);
    }

    /**
     * 查询真实运行时、数据产品和模拟部署的健康摘要。
     *
     * @return 结构化健康响应
     */
    public Map<String, Object> health() {
        if (serviceToken.length() < 32) {
            throw new AgentContractException(503, "RUNTIME_NOT_CONFIGURED", "量化运行时服务令牌未配置");
        }
        HttpRequest request = HttpRequest.newBuilder(healthUri)
                .timeout(Duration.ofSeconds(10))
                .header("X-Quant-Runtime-Token", serviceToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
            Map<String, Object> payload = objectMapper.readValue(
                    response.body(),
                    new TypeReference<LinkedHashMap<String, Object>>() { });
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AgentContractException(502, "QUANTITATIVE_RUNTIME_REJECTED", "量化运行时健康查询失败");
            }
            return payload;
        } catch (AgentContractException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    /**
     * 校验运行时基地址仅使用 HTTP 或 HTTPS。
     *
     * @param runtimeUrl 待校验地址
     * @return 规范化基地址
     */
    private URI validateBaseUri(String runtimeUrl) {
        URI value = URI.create(runtimeUrl == null ? "" : runtimeUrl.trim());
        if (!("http".equalsIgnoreCase(value.getScheme()) || "https".equalsIgnoreCase(value.getScheme()))
                || value.getHost() == null) {
            throw new IllegalArgumentException("量化运行时地址无效");
        }
        String normalized = value.toString().endsWith("/") ? value.toString() : value + "/";
        return URI.create(normalized);
    }

    /** 创建不泄露内部地址、令牌和响应正文的统一不可用错误。 */
    private AgentContractException unavailable() {
        return new AgentContractException(502, "QUANTITATIVE_RUNTIME_UNAVAILABLE", "量化运行时不可用");
    }
}
