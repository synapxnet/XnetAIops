package com.synapxnet.aiopsmonservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NotifyGroup {
    private Long id;
    private String groupName;
    private String notifyType;
    private String webhookUrl;
    private LocalDateTime createdAt;
}
