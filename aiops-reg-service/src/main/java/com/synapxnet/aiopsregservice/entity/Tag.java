package com.synapxnet.aiopsregservice.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Tag {
    private Long id;
    private Long repositoryId;
    private String tagName;
    private String digest;
    private Long sizeBytes;
    private String architecture;
    private String os;
    private LocalDateTime pushedAt;
}
