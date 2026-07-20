package com.synapxnet.aiopsk8sservice.service;

import java.util.List;
import java.util.Map;

public interface K8sNetworkPolicyService {
    List<Map<String, Object>> listNetworkPolicies(Long clusterId, String namespace);
    Map<String, Object> getNetworkPolicy(Long clusterId, String namespace, String name);
    void createNetworkPolicyFromYaml(Long clusterId, String namespace, String yaml);
    void deleteNetworkPolicy(Long clusterId, String namespace, String name);
}
