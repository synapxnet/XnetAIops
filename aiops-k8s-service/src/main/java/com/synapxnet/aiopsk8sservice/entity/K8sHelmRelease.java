package com.synapxnet.aiopsk8sservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class K8sHelmRelease {
    private Long id;
    private Long clusterId;
    private String namespace;
    private String releaseName;
    private String chartName;
    private String chartVersion;
    private String appVersion;
    private String valuesOverride;
    private String status;
    private Integer revision;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
