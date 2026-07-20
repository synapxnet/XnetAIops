package com.synapxnet.aiopsk8sservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class K8sClusterMetricsSnapshot {
    private Long id;
    private Long clusterId;
    private Double cpuCapacity;
    private Double cpuUsed;
    private Long memoryCapacity;
    private Long memoryUsed;
    private Integer podCapacity;
    private Integer podUsed;
    private Long storageCapacity;
    private Long storageUsed;
    private LocalDateTime snapshotTime;
}
