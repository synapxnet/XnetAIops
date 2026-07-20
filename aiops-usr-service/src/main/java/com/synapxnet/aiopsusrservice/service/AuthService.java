package com.synapxnet.aiopsusrservice.service;

import java.util.List;
import java.util.Map;

public interface AuthService {

    /**
     * 发送短信验证码
     */
    Map<String, Object> sendSmsCode(String userPhone);

    /**
     * 验证码登录
     */
    Map<String, Object> login(String userPhone, String code);

    /**
     * 退出登录
     */
    void logout(String token);

    /**
     * 获取用户信息
     */
    Map<String, Object> getUserInfo(String token);

    /**
     * 获取权限码
     */
    List<String> getAccessCodes(String token);
}
