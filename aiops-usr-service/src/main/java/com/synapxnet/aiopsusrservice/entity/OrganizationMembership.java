package com.synapxnet.aiopsusrservice.entity;

import lombok.Data;

/**
 * 当前用户在租户、部门和团队中的有效成员关系。
 */
@Data
public class OrganizationMembership {
    private String tenantUid;
    private String tenantName;
    private String deptUid;
    private String deptName;
    private String teamUid;
    private String teamName;
}
