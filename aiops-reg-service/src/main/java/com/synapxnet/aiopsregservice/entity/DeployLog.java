package com.synapxnet.aiopsregservice.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeployLog {
    private Long id;
    private Long registryId;
    private String action; // install, upgrade, uninstall, start, stop, restart
    private String status; // running, success, failed
    private String logText;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
