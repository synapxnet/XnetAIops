package com.synapxnet.aiopsregservice.service;

import com.synapxnet.aiopsregservice.entity.DeployLog;

import java.util.List;

public interface RegistryDeployService {
    void deploy(Long registryId);
    void undeploy(Long registryId);
    void start(Long registryId);
    void stop(Long registryId);
    void restart(Long registryId);
    void upgrade(Long registryId);
    void cancelDeploy(Long registryId);
    List<DeployLog> getDeployLogs(Long registryId);
}
