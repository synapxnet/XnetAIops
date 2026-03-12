package com.synapxnet.aiopsk8sservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class K8sDeployLog {
    private Long id;
    private Long planId;
    private String nodeHost;
    private String step;
    private String logLevel;
    private String message;
    private LocalDateTime createdAt;
}
