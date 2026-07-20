package com.synapxnet.aiopsregservice.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Registry {
    private Long id;
    private String uid;
    private String registryName;
    private String registryType; // harbor, gitlab, docker_distribution
    private String description;
    private String version;
    private String status; // not_deployed, deploying, running, stopped, failed, uninstalling
    private String deployMode; // ssh, k8s

    // SSH deploy fields
    private Long hostId;
    private String host;
    private Integer sshPort;
    private String sshUser;
    private String encryptedPassword;
    private String encryptedPrivateKey;
    private String installPath;
    private Integer servicePort; // Registry service port (Harbor:80, GitLab:80, Distribution:5000)

    // K8s deploy fields
    private Long clusterId;
    private String namespace;
    private String releaseName;
    private String helmValues;

    // Registry access info
    private String endpoint;
    private String apiUrl;
    private String adminUser;
    private String encryptedAdminPassword;
    private Boolean useSsl;
    private String certPem;

    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
