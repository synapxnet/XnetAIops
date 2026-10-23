/* Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 企业一次性登录验证码 / Enterprise one-time login verification.
 * Author: maoyo | Department: 研发部 | Date: 2026-10-23 | Version: 1.0.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopsusrservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class LoginVerificationService {
    private static final String PREFIX = "xnet:aiops:login:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> STORE = new DefaultRedisScript<>(
            "redis.call('HSET', KEYS[1], 'code', ARGV[1], 'attempts', '0'); "
            + "redis.call('EXPIRE', KEYS[1], 300); return 1", Long.class);
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>(
            "local code = redis.call('HGET', KEYS[1], 'code'); if not code then return 0 end; "
            + "if code == ARGV[1] then redis.call('DEL', KEYS[1]); return 1 end; "
            + "local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1); "
            + "if attempts >= 5 then redis.call('DEL', KEYS[1]) end; return 0", Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final String gatewayUrl;
    private final String gatewayToken;
    private final HttpClient client;

    /** 注入企业网关配置，不提供演示回退。 Inject the enterprise gateway without demo fallback. */
    @Autowired
    public LoginVerificationService(StringRedisTemplate redis, ObjectMapper mapper,
            @Value("${XNET_SMS_GATEWAY_URL:}") String gatewayUrl,
            @Value("${XNET_SMS_GATEWAY_TOKEN:}") String gatewayToken) {
        this(redis, mapper, gatewayUrl, gatewayToken,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    /** 允许测试注入受控传输。 Allow tests to inject a controlled transport. */
    LoginVerificationService(StringRedisTemplate redis, ObjectMapper mapper,
            String gatewayUrl, String gatewayToken, HttpClient client) {
        this.redis = redis;
        this.mapper = mapper;
        this.gatewayUrl = gatewayUrl == null ? "" : gatewayUrl.trim();
        this.gatewayToken = gatewayToken == null ? "" : gatewayToken.trim();
        this.client = client;
    }

    /** 限频发送随机验证码；网关失败不产生可用验证码。 Rate-limit random codes and invalidate failed deliveries. */
    public void send(String phone) {
        if (!validPhone(phone)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入有效手机号");
        URI uri;
        try {
            uri = URI.create(gatewayUrl);
            boolean local = List.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost());
            if (gatewayToken.isBlank() || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getFragment() != null || !("https".equals(uri.getScheme())
                    || (local && "http".equals(uri.getScheme())))) throw new IllegalArgumentException();
        } catch (Exception invalid) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "登录短信服务尚未配置，请联系管理员");
        }
        String key = PREFIX + phone;
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key + ":send", "1", Duration.ofSeconds(60)))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请一分钟后重试");
        }
        String code = String.format(java.util.Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        try {
            redis.execute(STORE, List.of(key), code);
            String body = mapper.writeValueAsString(Map.of("phone", phone, "code", code,
                    "purpose", "login", "expiresInSeconds", 300));
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + gatewayToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300
                    || !mapper.readTree(response.body()).path("accepted").asBoolean(false)) {
                throw new IllegalStateException("delivery rejected");
            }
        } catch (Exception failed) {
            redis.delete(key);
            if (failed instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "验证码发送失败，请稍后重试");
        }
    }

    /** 原子核验并销毁验证码，失败最多五次。 Atomically consume a valid code and allow at most five failures. */
    public boolean verify(String phone, String code) {
        if (!validPhone(phone) || code == null || !code.matches("[0-9]{6}")) return false;
        return Long.valueOf(1).equals(redis.execute(CONSUME, List.of(PREFIX + phone), code));
    }

    /** 校验手机号格式。 Validate phone number syntax. */
    private static boolean validPhone(String phone) {
        return phone != null && phone.matches("1[0-9]{10}");
    }
}
