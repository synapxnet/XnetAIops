package com.synapxnet.aiopsregservice.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Repository {
    private Long id;
    private Long registryId;
    private Long projectId;
    private String repoName;
    private Integer tagsCount;
    private Long pullCount;
    private String latestTag;
    private LocalDateTime updatedAt;
}
