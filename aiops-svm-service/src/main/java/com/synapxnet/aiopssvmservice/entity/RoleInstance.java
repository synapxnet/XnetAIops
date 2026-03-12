package com.synapxnet.aiopssvmservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RoleInstance {
    private Long id;
    private String uid;
    private Long serviceInstanceId;
    private Long roleDefId;
    private String roleName;
    private String roleType;
    private Long hostId;
    private String hostname;
    private String status;
    private Boolean needRestart;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
