package com.synapxnet.aiopsclmservice.entity;

import lombok.Data;

@Data
public class ClusterVariable {
    private Long id;
    private Long clusterId;
    private String variableName;
    private String variableValue;
}
