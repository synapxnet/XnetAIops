package com.synapxnet.aiopsmonservice.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AlertRule {
    private Long id;
    private String uid;
    private Long clusterId;
    private String ruleName;
    private String serviceName;
    private String expression;
    private String compareMethod;
    private BigDecimal thresholdValue;
    private String alertLevel;
    private Integer durationSeconds;
    private Boolean enabled;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
