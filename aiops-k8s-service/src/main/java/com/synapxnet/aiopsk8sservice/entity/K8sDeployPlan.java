package com.synapxnet.aiopsk8sservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class K8sDeployPlan {
    private Long id;
    private String uid;
    private String planName;
    private String k8sVersion;
    private String deployType;
    private String networkPlugin;
    private String containerRuntime;
    private String podCidr;
    private String serviceCidr;
    private Boolean installMetricsServer;
    private Boolean installIngressNginx;
    private String storagePlugin;
    private String registryUrl;
    private String status;
    private Long resultClusterId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
