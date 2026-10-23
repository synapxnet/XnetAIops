/* Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. No copying, distribution or use without authorization.
 * 主线认证边界 / Mainline authentication boundaries.
 * Author: maoyo | Department: 研发部 | Date: 2026-10-23 | Version: 1.0.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopsusrservice.service;

import com.synapxnet.aiopsusrservice.entity.User;
import com.synapxnet.aiopsusrservice.mapper.RoleMapper;
import com.synapxnet.aiopsusrservice.mapper.UserMapper;
import com.synapxnet.aiopsusrservice.security.jwt.JwtUtil;
import com.synapxnet.aiopsusrservice.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthMainlineTest {
    /** 普通注册账号能收到验证码并登录，不需要比赛白名单。 Ordinary registered accounts can receive codes and log in without a competition allowlist. */
    @Test
    void supportsRegisteredEnterpriseAccounts() {
        var users = mock(UserMapper.class);
        var codes = mock(LoginVerificationService.class);
        var jwt = mock(JwtUtil.class);
        var service = new AuthServiceImpl(mock(RoleMapper.class), users, codes);
        ReflectionTestUtils.setField(service, "jwtUtil", jwt);
        var user = new User();
        user.setId(7L); user.setPhone("13912345678"); user.setStatus("active");
        when(users.findByPhone(user.getPhone())).thenReturn(user);
        when(codes.verify(user.getPhone(), "765432")).thenReturn(true);
        when(jwt.generateToken(user.getPhone())).thenReturn("test-token");
        assertEquals(true, service.sendSmsCode(user.getPhone()).get("sent"));
        verify(codes).send(user.getPhone());
        assertEquals("test-token", service.login(user.getPhone(), "765432").get("accessToken"));
    }

    /** 历史演示码与手机号不构成登录特权。 Historical demo identities and codes confer no login privilege. */
    @Test
    void rejectsOldFixedCode() {
        var users = mock(UserMapper.class);
        var service = new AuthServiceImpl(mock(RoleMapper.class), users, mock(LoginVerificationService.class));
        assertThrows(IllegalArgumentException.class, () -> service.login("17870171303", "000000"));
        verifyNoInteractions(users);
    }

    /** 未注册或停用账号不可请求短信。 Unknown or disabled accounts cannot request an SMS. */
    @Test
    void rejectsInactiveAccountsBeforeDelivery() {
        var users = mock(UserMapper.class);
        var codes = mock(LoginVerificationService.class);
        var service = new AuthServiceImpl(mock(RoleMapper.class), users, codes);
        assertThrows(IllegalArgumentException.class, () -> service.sendSmsCode("13912345678"));
        var user = new User(); user.setStatus("disabled");
        when(users.findByPhone("13912345678")).thenReturn(user);
        assertThrows(IllegalArgumentException.class, () -> service.sendSmsCode("13912345678"));
        verifyNoInteractions(codes);
    }

    /** 停用账号不能凭旧JWT继续获取身份与权限。 Disabled accounts cannot reuse an old JWT to obtain identity or access. */
    @Test
    void rejectsDisabledIdentityAndPermissionsForExistingToken() {
        var users = mock(UserMapper.class);
        var roles = mock(RoleMapper.class);
        var jwt = mock(JwtUtil.class);
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(jwt.extractUsername("old-token")).thenReturn("13912345678");
        var user = new User(); user.setId(7L); user.setStatus("disabled"); user.setUserType("admin");
        when(users.findByPhone("13912345678")).thenReturn(user);
        var service = new AuthServiceImpl(roles, users, mock(LoginVerificationService.class));
        ReflectionTestUtils.setField(service, "jwtUtil", jwt);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
        assertThrows(IllegalArgumentException.class, () -> service.getUserInfo("old-token"));
        assertThrows(IllegalArgumentException.class, () -> service.getAccessCodes("old-token"));
        verifyNoInteractions(roles);
    }
}
