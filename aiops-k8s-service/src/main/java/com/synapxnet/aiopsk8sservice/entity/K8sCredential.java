package com.synapxnet.aiopsk8sservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class K8sCredential {
    private Long id;
    private Long projectId;
    private String name;
    private String type;
    private String description;
    private String username;
    private String password;
    private String privateKey;
    private String passphrase;
    private String token;
    private String kubeconfig;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
