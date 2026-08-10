package com.synapxnet.aiopsusrservice.controller;

import com.synapxnet.aiopsusrservice.common.Result;
import com.synapxnet.aiopsusrservice.entity.OrganizationTreeNode;
import com.synapxnet.aiopsusrservice.service.OrganizationAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/usr")
public class OrganizationController {
    private final OrganizationAccessService organizationAccessService;

    /**
     * 创建组织查询控制器。
     *
     * @param organizationAccessService 组织权限服务
     */
    public OrganizationController(OrganizationAccessService organizationAccessService) {
        this.organizationAccessService = organizationAccessService;
    }

    /**
     * 返回当前登录用户可见的租户、部门和团队树。
     *
     * @param authorization Bearer 认证头
     * @return 当前用户可见的组织树响应
     */
    @GetMapping("/organization-tree")
    public Result<List<OrganizationTreeNode>> getOrganizationTree(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return Result.success(organizationAccessService.getVisibleOrganizationTree(authorization));
    }
}
