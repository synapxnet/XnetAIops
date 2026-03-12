package com.synapxnet.aiopssvmservice.entity;

import lombok.Data;

@Data
public class RoleDef {
    private Long id;
    private Long serviceDefId;
    private String roleName;
    private String roleType;
    private String cardinality;
    private Integer jmxPort;
    private String logFile;
}
