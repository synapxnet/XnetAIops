package com.synapxnet.aiopsusrservice.entity;

import lombok.Data;

@Data
public class UserRoleCluster {
    private Long id;
    private Long userId;
    private Long roleId;
    private Long clusterId;

    // Joined fields for display
    private String username;
    private String roleName;
    private String roleCode;
}
