package com.synapxnet.aiopsusrservice.controller;

import com.synapxnet.aiopsusrservice.common.Result;
import com.synapxnet.aiopsusrservice.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usr")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 发送短信验证码
     */
    @PostMapping("/sms-code")
    public Result<Map<String, Object>> sendSmsCode(@RequestBody Map<String, String> params) {
        String userPhone = params.get("userPhone");
        return Result.success(authService.sendSmsCode(userPhone));
    }

    /**
     * 验证码登录
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> params) {
        String userPhone = params.get("userPhone");
        String code = params.get("code");
        return Result.success(authService.login(userPhone, code));
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token != null) {
            authService.logout(token);
        }
        return Result.success();
    }

    /**
     * 获取用户信息
     */
    @GetMapping("/user/info")
    public Result<Map<String, Object>> getUserInfo(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return Result.success(authService.getUserInfo(token));
    }

    /**
     * 获取权限码
     */
    @GetMapping("/auth/codes")
    public Result<List<String>> getAccessCodes(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return Result.success(authService.getAccessCodes(token));
    }
}
