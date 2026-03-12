package com.synapxnet.aiopsk8sservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class K8sPipeline {
    private Long id;
    private Long projectId;
    private String name;
    private String description;
    private String type;
    private String jenkinsfile;
    private String sourceType;
    private String sourceUrl;
    private String sourceBranch;
    private Long credentialId;
    private Boolean disableConcurrent;
    private String timerTrigger;
    private String jenkinsJobName;
    private String jenkinsJobPath;
    private String status;
    private String lastRunStatus;
    private LocalDateTime lastRunTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
