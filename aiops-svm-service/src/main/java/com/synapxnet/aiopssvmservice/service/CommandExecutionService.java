package com.synapxnet.aiopssvmservice.service;

import com.synapxnet.aiopssvmservice.entity.Command;

public interface CommandExecutionService {

    /**
     * Execute a service-level command (install/start/stop/restart/config_update).
     * Creates Command, CommandHost, CommandHostRole records and executes asynchronously via SSH.
     */
    Command executeServiceCommand(Long serviceInstanceId, String commandType, String createdBy);

    /**
     * Cancel a running command.
     */
    void cancelCommand(Long commandId);
}
