package com.synapxnet.aiopsk8sservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class K8sAlertRule {
    private Long id;
    private Long clusterId;
    private String name;
    private String description;
    private String severity;       // critical, warning, info
    private String resourceType;   // node, pod, deployment, cluster
    private String metricName;
    private String condition;      // >, <, >=, <=, ==
    private Double threshold;
    private String duration;       // e.g., 5m, 10m
    private Boolean enabled;
    private String notifyChannels; // JSON array: ["email","webhook"]
    private LocalDateTime lastTriggered;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
