package com.synapxnet.aiopsk8sservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class K8sDevopsProject {
    private Long id;
    private String name;
    private String description;
    private String jenkinsUrl;
    private String jenkinsUser;
    private String jenkinsToken;
    private Long clusterId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Transient fields for list display
    private Integer pipelineCount;
}
