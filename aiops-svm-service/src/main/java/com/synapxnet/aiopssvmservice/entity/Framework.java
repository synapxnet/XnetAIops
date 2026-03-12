package com.synapxnet.aiopssvmservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Framework {
    private Long id;
    private String frameName;
    private String frameCode;
    private String frameVersion;
    private String description;
    private LocalDateTime createdAt;
}
