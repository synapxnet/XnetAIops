package com.synapxnet.aiopsregservice.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RegistryProject {
    private Long id;
    private Long registryId;
    private String projectName;
    private String visibility; // public, private
    private Integer repoCount;
    private LocalDateTime createdAt;
}
