package com.synapxnet.aiopsusrservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Role {
    private Long id;
    private String roleName;
    private String roleCode;
    private String description;
    private LocalDateTime createdAt;
}
