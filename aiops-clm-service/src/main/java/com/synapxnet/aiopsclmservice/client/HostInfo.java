package com.synapxnet.aiopsclmservice.client;

import lombok.Data;

@Data
public class HostInfo {
    private Long id;
    private String hostname;
    private String ipAddress;
    private Integer sshPort;
    private String sshUser;
    private String authType;
    private String encryptedPassword;
    private String privateKey;
    private String osType;
    private String osVersion;
    private Integer cpuCores;
    private String status;
}
