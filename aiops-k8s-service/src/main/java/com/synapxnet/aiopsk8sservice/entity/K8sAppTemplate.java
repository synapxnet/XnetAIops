package com.synapxnet.aiopsk8sservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class K8sAppTemplate {
    private Long id;
    private String name;
    private String displayName;
    private String description;
    private String category;
    private String icon;
    private String helmRepoName;
    private String helmRepoUrl;
    private String chartName;
    private String chartVersion;
    private String defaultValues;
    private String docUrl;
    private Integer isFeatured;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
