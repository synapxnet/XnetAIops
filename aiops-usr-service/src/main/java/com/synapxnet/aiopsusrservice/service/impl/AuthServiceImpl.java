package com.synapxnet.aiopsusrservice.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapxnet.aiopsusrservice.entity.User;
import com.synapxnet.aiopsusrservice.mapper.UserMapper;
import com.synapxnet.aiopsusrservice.security.jwt.JwtUtil;
import com.synapxnet.aiopsusrservice.service.AuthService;
import com.synapxnet.aiopsusrservice.service.SMSCodeService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private JwtUtil jwtUtil;

    @Value("${Apps.Name}")
    private String appName;

    public AuthServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Map<String, Object> sendSmsCode(String userPhone) {
        User user = userMapper.findByPhone(userPhone);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        // 生成6位随机验证码
        String verificationCode = String.format("%06d", new SecureRandom().nextInt(999999));

        // 存储验证码到Redis，5分钟有效
        stringRedisTemplate.opsForValue().set(
                "code:" + userPhone,
                verificationCode,
                5, TimeUnit.MINUTES
        );

        // 通过SMS服务发送验证码
        try {
            String[] params = {
                    appName,
                    verificationCode,
                    "5",
                    userPhone
            };
            String smsResponse = SMSCodeService.SMSCodeSend(params);

            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> smsResult = objectMapper.readValue(
                    smsResponse,
                    new TypeReference<Map<String, Object>>() {}
            );

            Integer smsCode = (Integer) smsResult.get("code");
            if (smsCode != null && smsCode == 200) {
                String requestId = (String) smsResult.get("request_id");
                Map<String, Object> result = new HashMap<>();
                result.put("sms_request_id", requestId);
                return result;
            } else {
                String smsMsg = (String) smsResult.get("msg");
                throw new RuntimeException("短信发送失败: " + smsMsg);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("短信发送失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> login(String userPhone, String code) {
        // 从Redis获取存储的验证码
        String storedCode = stringRedisTemplate.opsForValue().get("code:" + userPhone);
        if (storedCode == null || !storedCode.equals(code)) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }
        // 验证成功后删除验证码（一次性使用）
        stringRedisTemplate.delete("code:" + userPhone);

        User user = userMapper.findByPhone(userPhone);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        if (!"active".equals(user.getStatus())) {
            throw new IllegalArgumentException("User is disabled");
        }

        // 生成JWT token
        String token = jwtUtil.generateToken(user.getPhone());

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", token);
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("userType", user.getUserType());
        return result;
    }

    @Override
    public void logout(String token) {
        try {
            var claims = jwtUtil.extractAllClaims(token);
            Date expiration = claims.getExpiration();
            long ttl = expiration.getTime() - System.currentTimeMillis();
            if (ttl > 0) {
                stringRedisTemplate.opsForValue().set(
                        "logout:" + token,
                        "invalid",
                        ttl,
                        TimeUnit.MILLISECONDS
                );
            }
        } catch (Exception e) {
            // token解析失败，忽略
        }
    }

    @Override
    public Map<String, Object> getUserInfo(String token) {
        String phone = extractPhoneFromToken(token);
        User user = userMapper.findByPhone(phone);
        if (user == null) {
            throw new IllegalArgumentException("Invalid or expired token");
        }

        Map<String, Object> info = new HashMap<>();
        info.put("userId", user.getId());
        info.put("username", user.getUsername());
        info.put("realName", user.getUsername());
        info.put("userType", user.getUserType());
        info.put("roles", List.of(user.getUserType()));
        info.put("avatar", "");
        info.put("homePath", "/dashboard/overview");
        info.put("desc", "");
        info.put("token", token);
        return info;
    }

    @Override
    public List<String> getAccessCodes(String token) {
        String phone = extractPhoneFromToken(token);
        User user = userMapper.findByPhone(phone);
        if (user == null) {
            throw new IllegalArgumentException("Invalid or expired token");
        }
        if ("admin".equals(user.getUserType())) {
            return List.of("AC_100100", "AC_100110", "AC_100120", "AC_100010");
        }
        return List.of("AC_100100");
    }

    private String extractPhoneFromToken(String token) {
        // 检查token是否在黑名单中
        String blacklisted = stringRedisTemplate.opsForValue().get("logout:" + token);
        if ("invalid".equals(blacklisted)) {
            throw new IllegalArgumentException("Invalid or expired token");
        }
        try {
            String phone = jwtUtil.extractUsername(token);
            if (phone == null || phone.isEmpty()) {
                throw new IllegalArgumentException("Invalid or expired token");
            }
            return phone;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid or expired token");
        }
    }
}
