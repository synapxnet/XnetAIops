/* Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 登录网关边界测试 / Login gateway boundary tests.
 * Author: maoyo | Department: 研发部 | Date: 2026-10-23 | Version: 1.0.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopsusrservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoginVerificationServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    /** 缓存过期、错误或复用均被拒绝，只有原子消费成功才能通过。 Reject expired, wrong and reused codes unless atomic consumption succeeds. */
    @Test void consumesSuccessfulCodesOnlyOnce() {
        var redis = mock(StringRedisTemplate.class);
        var service = new LoginVerificationService(redis, mapper, "", "");
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), eq("765432")))
                .thenReturn(1L, 0L);
        assertTrue(service.verify("13900000001", "765432"));
        assertFalse(service.verify("13900000001", "765432"));
        assertFalse(service.verify("13900000001", "123456"));
    }

    /** 未配置不允许发送，也不触碰缓存。 Missing gateway configuration cannot issue codes or change cache state. */
    @Test void unavailableGatewayFailsClosed() {
        var redis = mock(StringRedisTemplate.class);
        var service = new LoginVerificationService(redis, mapper, "", "");
        var error = assertThrows(ResponseStatusException.class, () -> service.send("13900000001"));
        assertEquals(503, error.getStatusCode().value());
        verifyNoInteractions(redis);
    }

    /** 原固定验证码无独立权限，仍需一次性缓存核验。 Former fixed codes must pass the same one-time verification. */
    @Test void oldDemoCodeHasNoSpecialTreatment() {
        var redis = mock(StringRedisTemplate.class);
        var service = new LoginVerificationService(redis, mapper, "", "");
        assertFalse(service.verify("17870171303", "000000"));
        assertFalse(service.verify(null, "000000"));
        assertFalse(service.verify("13900000001", "invalid"));
    }

    /** 用真实本机HTTP验证网关载荷，拒收时销毁缓存验证码。 Verify actual HTTP delivery and invalidate rejected codes. */
    @Test @SuppressWarnings("unchecked") void authenticatedGatewayControlsDelivery() throws Exception {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(60)))).thenReturn(true);
        AtomicReference<Map<?, ?>> payload = new AtomicReference<>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/login", exchange -> {
            assertEquals("Bearer test-gateway-token", exchange.getRequestHeaders().getFirst("Authorization"));
            payload.set(mapper.readValue(exchange.getRequestBody(), Map.class));
            byte[] response = "{\"accepted\":false}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var service = new LoginVerificationService(redis, mapper,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/login", "test-gateway-token", HttpClient.newHttpClient());
            var failure = assertThrows(ResponseStatusException.class, () -> service.send("13900000001"));
            assertEquals(503, failure.getStatusCode().value());
            assertEquals("13900000001", payload.get().get("phone"));
            assertEquals("login", payload.get().get("purpose"));
            assertEquals(300, payload.get().get("expiresInSeconds"));
            assertTrue(payload.get().get("code").toString().matches("[0-9]{6}"));
            verify(redis).delete("xnet:aiops:login:13900000001");
        } finally { server.stop(0); }
    }

    /** 重发限频发生在发送之前。 Rate limiting prevents a repeated outbound request. */
    @Test @SuppressWarnings("unchecked") void resendWaitIsEnforced() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);
        var service = new LoginVerificationService(redis, mapper, "https://sms.example.invalid/login", "test");
        assertEquals(429, assertThrows(ResponseStatusException.class,
                () -> service.send("13900000001")).getStatusCode().value());
    }
}
