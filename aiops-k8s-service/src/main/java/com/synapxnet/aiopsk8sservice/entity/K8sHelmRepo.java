package com.synapxnet.aiopsk8sservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class K8sHelmRepo {
    private Long id;
    private String name;
    private String url;
    private String description;
    private String authType;
    private String username;
    private String password;
    private String status;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
