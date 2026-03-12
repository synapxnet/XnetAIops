package com.synapxnet.aiopsk8sservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class K8sAlertHistory {
    private Long id;
    private Long ruleId;
    private Long clusterId;
    private String ruleName;
    private String severity;
    private String resourceType;
    private String resourceName;
    private String message;
    private String status;         // firing, resolved
    private Double currentValue;
    private Double threshold;
    private LocalDateTime firedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
}
