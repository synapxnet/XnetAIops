package com.synapxnet.aiopsusrservice.controller;

import com.synapxnet.aiopsusrservice.common.Result;
import com.synapxnet.aiopsusrservice.entity.OrganizationTreeNode;
import com.synapxnet.aiopsusrservice.service.OrganizationAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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

    /**
     * 为 Nginx 子请求校验当前用户是否可以读取指定团队数据。
     *
     * @param authorization Bearer 认证头
     * @param tenantUid 租户唯一编码
     * @param deptUid 部门唯一编码
     * @param teamUid 团队唯一编码
     * @return 允许时返回 204，否则返回 403
     */
    @GetMapping("/organization-access")
    public ResponseEntity<Void> checkOrganizationAccess(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Tenant-Uid", required = false) String tenantUid,
            @RequestHeader(value = "X-Dept-Uid", required = false) String deptUid,
            @RequestHeader(value = "X-Team-Uid", required = false) String teamUid
    ) {
        boolean allowed = organizationAccessService.hasDataAccess(
                authorization,
                tenantUid,
                deptUid,
                teamUid
        );
        return allowed
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}
