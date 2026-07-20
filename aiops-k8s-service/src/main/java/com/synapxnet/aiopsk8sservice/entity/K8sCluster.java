package com.synapxnet.aiopsk8sservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class K8sCluster {
    private Long id;
    private String uid;
    private String name;
    private String description;
    private String apiServerUrl;
    private String kubeconfigContent;
    private String version;
    private Integer nodeCount;
    private Integer namespaceCount;
    private String status;
    private String provider;
    private String networkPlugin;
    private String containerRuntime;
    private String sshHost;
    private Integer sshPort;
    private String sshUser;
    private String sshPassword;
    private String sshKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
