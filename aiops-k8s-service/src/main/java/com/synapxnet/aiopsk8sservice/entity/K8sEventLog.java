package com.synapxnet.aiopsk8sservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class K8sEventLog {
    private Long id;
    private Long clusterId;
    private String namespace;
    private String kind;
    private String name;
    private String eventType;
    private String reason;
    private String message;
    private String sourceComponent;
    private LocalDateTime firstTimestamp;
    private LocalDateTime lastTimestamp;
    private Integer count;
    private LocalDateTime createdAt;
}
