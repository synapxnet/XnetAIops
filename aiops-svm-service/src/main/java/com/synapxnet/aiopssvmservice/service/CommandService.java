package com.synapxnet.aiopssvmservice.service;

import com.synapxnet.aiopssvmservice.entity.Command;
import com.synapxnet.aiopssvmservice.entity.CommandHost;

import java.util.List;
import java.util.Map;

public interface CommandService {

    List<Command> listByClusterId(Long clusterId);

    List<Command> listAll();

    Map<String, Object> getDetail(Long id);

    Command create(Command command);

    Command updateStatus(Long id, String status, Integer progress);
}
