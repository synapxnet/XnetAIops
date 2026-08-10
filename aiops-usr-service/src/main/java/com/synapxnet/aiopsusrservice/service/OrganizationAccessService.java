package com.synapxnet.aiopsusrservice.service;

import com.synapxnet.aiopsusrservice.entity.OrganizationMembership;
import com.synapxnet.aiopsusrservice.entity.OrganizationTreeNode;
import com.synapxnet.aiopsusrservice.entity.User;
import com.synapxnet.aiopsusrservice.mapper.OrganizationMapper;
import com.synapxnet.aiopsusrservice.mapper.UserMapper;
import com.synapxnet.aiopsusrservice.security.jwt.JwtUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class OrganizationAccessService {
    private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";

    private final JwtUtil jwtUtil;
    private final OrganizationMapper organizationMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserMapper userMapper;

    /**
     * 创建组织权限服务并注入认证和成员关系依赖。
     *
     * @param jwtUtil JWT 解析组件
     * @param organizationMapper 组织成员关系查询组件
     * @param stringRedisTemplate 退出令牌黑名单存储
     * @param userMapper 用户查询组件
     */
    public OrganizationAccessService(
            JwtUtil jwtUtil,
            OrganizationMapper organizationMapper,
            StringRedisTemplate stringRedisTemplate,
            UserMapper userMapper
    ) {
        this.jwtUtil = jwtUtil;
        this.organizationMapper = organizationMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.userMapper = userMapper;
    }

    /**
     * 根据登录令牌返回当前用户被明确授权的组织树。
     *
     * @param authorization Bearer 认证头
     * @return 当前用户可见的组织树
     */
    public List<OrganizationTreeNode> getVisibleOrganizationTree(String authorization) {
        String phone = resolveAuthenticatedPhone(authorization);
        User user = userMapper.findByPhone(phone);
        if (user == null || !"active".equals(user.getStatus())) {
            throw new IllegalArgumentException(INVALID_TOKEN_MESSAGE);
        }
        return buildOrganizationTree(organizationMapper.findActiveMembershipsByPhone(phone));
    }

    /**
     * 验证 Bearer 令牌、退出黑名单和 JWT 签名并解析登录手机号。
     *
     * @param authorization Bearer 认证头
     * @return 已认证手机号
     */
    private String resolveAuthenticatedPhone(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException(INVALID_TOKEN_MESSAGE);
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isEmpty() || "invalid".equals(stringRedisTemplate.opsForValue().get("logout:" + token))) {
            throw new IllegalArgumentException(INVALID_TOKEN_MESSAGE);
        }
        try {
            String phone = jwtUtil.extractUsername(token);
            if (phone == null || phone.isBlank()) {
                throw new IllegalArgumentException(INVALID_TOKEN_MESSAGE);
            }
            return phone;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(INVALID_TOKEN_MESSAGE);
        }
    }

    /**
     * 把扁平成员关系转换为去重且保持顺序的租户、部门、团队树。
     *
     * @param memberships 当前用户的有效成员关系
     * @return 可供前端级联选择的组织树
     */
    private List<OrganizationTreeNode> buildOrganizationTree(List<OrganizationMembership> memberships) {
        List<OrganizationTreeNode> tenants = new ArrayList<>();
        for (OrganizationMembership membership : memberships) {
            OrganizationTreeNode tenant = getOrCreateNode(
                    tenants,
                    membership.getTenantName(),
                    membership.getTenantUid()
            );
            if (membership.getDeptUid() == null) {
                continue;
            }
            OrganizationTreeNode department = getOrCreateNode(
                    tenant.getChildren(),
                    membership.getDeptName(),
                    membership.getDeptUid()
            );
            if (membership.getTeamUid() != null) {
                getOrCreateNode(
                        department.getChildren(),
                        membership.getTeamName(),
                        membership.getTeamUid()
                );
            }
        }
        return tenants;
    }

    /**
     * 按唯一编码复用已有节点，不存在时创建并追加节点。
     *
     * @param nodes 同一层级的节点列表
     * @param label 组织显示名称
     * @param value 组织唯一编码
     * @return 已存在或新创建的节点
     */
    private OrganizationTreeNode getOrCreateNode(
            List<OrganizationTreeNode> nodes,
            String label,
            String value
    ) {
        for (OrganizationTreeNode node : nodes) {
            if (node.getValue().equals(value)) {
                return node;
            }
        }
        OrganizationTreeNode node = new OrganizationTreeNode(label, value);
        nodes.add(node);
        return node;
    }
}
