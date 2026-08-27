package com.synapxnet.aiopssvmservice.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** 验证 AIOps 量化运行时健康路由的资源边界。 */
class QuantitativeRuntimeHealthClientTest {

    /** 确认存在测试构造器时生产构造器仍被明确注册为 Spring 注入入口。 */
    @Test
    void marksProductionConstructorForSpringInjection() throws NoSuchMethodException {
        assertTrue(QuantitativeRuntimeHealthClient.class
                .getConstructor(String.class, String.class, ObjectMapper.class)
                .isAnnotationPresent(Autowired.class));
    }

    /** 确认健康客户端只接管量化研究信号服务。 */
    @Test
    void supportsOnlyQuantitativeSignalService() {
        QuantitativeRuntimeHealthClient client = new QuantitativeRuntimeHealthClient(
                "http://127.0.0.1:8091",
                "x".repeat(32),
                new ObjectMapper(),
                mock(HttpClient.class));
        assertTrue(client.supports("service_quant_signal"));
        assertFalse(client.supports("service_rec_inference"));
        assertFalse(client.supports("service_unknown"));
    }
}
