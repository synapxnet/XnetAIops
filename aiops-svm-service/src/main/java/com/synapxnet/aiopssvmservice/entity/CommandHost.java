package com.synapxnet.aiopssvmservice.entity;

import lombok.Data;

@Data
public class CommandHost {
    private Long id;
    private Long commandId;
    private Long hostId;
    private String hostname;
    private String status;
    private Integer progress;
    private String resultMsg;
}
