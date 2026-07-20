package com.synapxnet.aiopsk8sservice.service;

import com.synapxnet.aiopsk8sservice.entity.K8sDeployLog;
import com.synapxnet.aiopsk8sservice.entity.K8sDeployNode;
import com.synapxnet.aiopsk8sservice.entity.K8sDeployPlan;

import java.util.List;
import java.util.Map;

public interface K8sDeployService {
    // Plans
    List<K8sDeployPlan> listPlans();
    Map<String, Object> getPlanDetail(Long planId);
    K8sDeployPlan createPlan(K8sDeployPlan plan, List<K8sDeployNode> nodes);
    void deletePlan(Long planId);

    // Nodes
    void addNode(Long planId, K8sDeployNode node);
    void removeNode(Long nodeId);
    boolean validateNode(Long nodeId);

    // Execution
    void executePlan(Long planId);
    void scaleOutNode(Long planId, K8sDeployNode node);
    void asyncScaleOut(Long planId, Long nodeId);
    List<K8sDeployLog> getLogs(Long planId);

    // Supported options
    List<String> getSupportedVersions();
    List<Map<String, String>> getSupportedCni();
    List<Map<String, String>> getSupportedCri();
    List<Map<String, String>> getSupportedStorage();
}
