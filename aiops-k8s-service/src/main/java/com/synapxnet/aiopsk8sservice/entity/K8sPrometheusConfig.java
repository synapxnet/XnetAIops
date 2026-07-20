package com.synapxnet.aiopsk8sservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class K8sPrometheusConfig {
    private Long id;
    private Long clusterId;
    private String prometheusUrl;
    private String authType;
    private String authToken;
    private String username;
    private String password;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
