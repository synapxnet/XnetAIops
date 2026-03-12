package com.synapxnet.aiopssvmservice.entity;

import lombok.Data;

@Data
public class ServiceDef {
    private Long id;
    private Long frameworkId;
    private String serviceName;
    private String serviceLabel;
    private String serviceVersion;
    private String description;
    private String dependencies;
    private String packageName;
    private String configJson;
    private Integer sortOrder;
}
