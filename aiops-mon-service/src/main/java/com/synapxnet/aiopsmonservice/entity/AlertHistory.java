package com.synapxnet.aiopsmonservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AlertHistory {
    private Long id;
    private String uid;
    private Long clusterId;
    private Long alertRuleId;
    private String alertName;
    private String hostname;
    private String alertLevel;
    private String alertInfo;
    private String alertAdvice;
    private String status;
    private LocalDateTime triggeredAt;
    private LocalDateTime resolvedAt;
}
