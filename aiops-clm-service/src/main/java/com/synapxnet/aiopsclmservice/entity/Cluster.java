package com.synapxnet.aiopsclmservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Cluster {
    private Long id;
    private String uid;
    private String clusterName;
    private String clusterCode;
    private String description;
    private String clusterType;
    private String status;
    private Integer totalHosts;
    private Integer runningServices;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
