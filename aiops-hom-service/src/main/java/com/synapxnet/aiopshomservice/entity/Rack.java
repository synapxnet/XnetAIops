package com.synapxnet.aiopshomservice.entity;

import lombok.Data;

@Data
public class Rack {
    private Long id;
    private Long clusterId;
    private String rackName;
    private String description;
}
