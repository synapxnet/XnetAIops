package com.synapxnet.aiopsusrservice.service.impl;

import com.synapxnet.aiopsusrservice.entity.User;
import com.synapxnet.aiopsusrservice.mapper.RoleMapper;
import com.synapxnet.aiopsusrservice.mapper.UserMapper;
import com.synapxnet.aiopsusrservice.security.jwt.JwtUtil;
import com.synapxnet.aiopsusrservice.service.AuthService;
import com.synapxnet.aiopsusrservice.service.LoginVerificationService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class AuthServiceImpl implements AuthService {

    private final RoleMapper roleMapper;
    private final UserMapper userMapper;
    private final LoginVerificationService verificationService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private JwtUtil jwtUtil;

    /** 注入用户权限和一次性验证服务。 Inject users, permissions and one-time verification. */
    public AuthServiceImpl(RoleMapper roleMapper, UserMapper userMapper, LoginVerificationService verificationService) {
        this.roleMapper = roleMapper;
        this.userMapper = userMapper;
        this.verificationService = verificationService;
    }

    /** 向实际注册的有效账号发送登录码。 Send a login code to an active registered account. */
    @Override
    public Map<String, Object> sendSmsCode(String userPhone) {
        User user = userMapper.findByPhone(userPhone);
        if (user == null || !"active".equals(user.getStatus())) {
            throw new IllegalArgumentException("账号不存在或已停用，请联系管理员");
        }
        verificationService.send(userPhone);
        return Map.of("sent", true, "expiresInSeconds", 300);
    }

    /** 登录依赖一次性验证码，不再接受固定比赛码。 Authenticate with a one-time code, never a fixed competition code. */
    @Override
    public Map<String, Object> login(String userPhone, String code) {
        if (!verificationService.verify(userPhone, code)) {
            throw new IllegalArgumentException("手机号或验证码错误，验证码可能已过期");
        }

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

    /** 停用后即使旧JWT仍有效也不返回身份。 Reject disabled identities even when an older JWT is valid. */
    @Override
    public Map<String, Object> getUserInfo(String token) {
        String phone = extractPhoneFromToken(token);
        User user = userMapper.findByPhone(phone);
        if (user == null || !"active".equals(user.getStatus())) {
            throw new IllegalArgumentException("Invalid or expired token");
        }

        Map<String, Object> info = new HashMap<>();
        info.put("userId", user.getId());
        info.put("username", user.getUsername());
        info.put("realName", user.getUsername());
        info.put("userType", user.getUserType());
        info.put("roles", resolveRoleCodes(user));
        info.put("avatar", "");
        info.put("homePath", "/dashboard/overview");
        info.put("desc", "");
        info.put("token", token);
        return info;
    }

    /** 每次读取权限都核对账号状态。 Recheck account status before returning access permissions. */
    @Override
    public List<String> getAccessCodes(String token) {
        String phone = extractPhoneFromToken(token);
        User user = userMapper.findByPhone(phone);
        if (user == null || !"active".equals(user.getStatus())) {
            throw new IllegalArgumentException("Invalid or expired token");
        }
        List<String> roleCodes = resolveRoleCodes(user);
        if ("admin".equals(user.getUserType()) || roleCodes.contains("ADMIN")) {
            return List.of("AC_100100", "AC_100110", "AC_100120", "AC_100010");
        }
        return roleCodes.isEmpty() ? List.of() : List.of("AC_100100");
    }

    /**
     * 返回数据库中明确分配给用户的角色编码，不存在映射时保持空权限。
     *
     * @param user 当前登录用户
     * @return 当前用户的有效角色编码
     */
    private List<String> resolveRoleCodes(User user) {
        List<String> roleCodes = roleMapper.findByUserId(user.getId()).stream()
                .map(mapping -> mapping.getRoleCode())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return roleCodes;
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
