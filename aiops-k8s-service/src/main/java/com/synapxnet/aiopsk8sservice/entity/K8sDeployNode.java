package com.synapxnet.aiopsk8sservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class K8sDeployNode {
    private Long id;
    private Long planId;
    private String host;
    private Integer sshPort;
    private String sshUser;
    private String sshPassword;
    private String sshKey;
    private String role;
    private String hostname;
    private String status;
    private String statusMessage;
    private LocalDateTime createdAt;
}
