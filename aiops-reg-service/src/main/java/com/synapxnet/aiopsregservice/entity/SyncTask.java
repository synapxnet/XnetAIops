package com.synapxnet.aiopsregservice.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SyncTask {
    private Long id;
    private Long registryId;
    private String sourceImage;       // e.g. docker.io/nginx:1.25
    private String targetProject;     // Harbor project name to push into
    private String syncMethod;        // harbor_replication, skopeo
    private Long harborPolicyId;      // Harbor replication policy ID (if harbor_replication)
    private Long harborExecutionId;   // Harbor replication execution ID
    private String status;            // pending, running, success, failed, cancelled
    private String statusDetail;      // Error message or progress info
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
