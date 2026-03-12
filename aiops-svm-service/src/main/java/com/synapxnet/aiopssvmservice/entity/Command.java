package com.synapxnet.aiopssvmservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Command {
    private Long id;
    private String uid;
    private Long clusterId;
    private String commandName;
    private String commandType;
    private String status;
    private Integer progress;
    private Long serviceInstanceId;
    private String createdBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
