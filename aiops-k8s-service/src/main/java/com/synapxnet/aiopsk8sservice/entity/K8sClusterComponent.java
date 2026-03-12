package com.synapxnet.aiopsk8sservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class K8sClusterComponent {
    private Long id;
    private Long clusterId;
    private String componentName;
    private String componentType;
    private String status;
    private String message;
    private LocalDateTime checkedAt;
}
