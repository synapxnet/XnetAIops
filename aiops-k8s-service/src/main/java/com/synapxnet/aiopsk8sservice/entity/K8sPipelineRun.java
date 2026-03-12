package com.synapxnet.aiopsk8sservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class K8sPipelineRun {
    private Long id;
    private Long pipelineId;
    private Integer runNumber;
    private String status;
    private String triggerType;
    private String triggerUser;
    private String parameters;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private String stagesStatus;
    private String logText;
    private String jenkinsBuildUrl;
    private LocalDateTime createdAt;
}
