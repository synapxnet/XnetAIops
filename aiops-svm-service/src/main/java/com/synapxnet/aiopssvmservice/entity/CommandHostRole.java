package com.synapxnet.aiopssvmservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CommandHostRole {
    private Long id;
    private Long commandHostId;
    private String roleName;
    private String roleType;
    private String status;
    private String resultMsg;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
