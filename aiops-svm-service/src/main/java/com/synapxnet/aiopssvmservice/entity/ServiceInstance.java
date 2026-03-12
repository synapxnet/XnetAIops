package com.synapxnet.aiopssvmservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ServiceInstance {
    private Long id;
    private String uid;
    private Long clusterId;
    private Long serviceDefId;
    private String serviceName;
    private String status;
    private String configJson;
    private Integer configVersion;
    private Boolean needRestart;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
