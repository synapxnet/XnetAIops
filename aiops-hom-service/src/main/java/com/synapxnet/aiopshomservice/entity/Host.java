package com.synapxnet.aiopshomservice.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Host {
    private Long id;
    private String uid;
    private Long clusterId;
    private String hostname;
    private String ipAddress;
    private Integer sshPort;
    private String sshUser;
    private String authType;
    private String encryptedPassword;
    private String privateKey;
    private String osType;
    private String osVersion;
    private String cpuArch;
    private Integer cpuCores;
    private BigDecimal totalMemGb;
    private BigDecimal totalDiskGb;
    private BigDecimal usedMemGb;
    private BigDecimal usedDiskGb;
    private BigDecimal cpuUsage;
    private String rack;
    private String nodeLabel;
    private String status;
    private String agentStatus;
    private LocalDateTime lastHeartbeat;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
