package com.synapxnet.aiopsk8sservice.service;

import java.util.List;
import java.util.Map;

public interface K8sConfigService {

    // ConfigMaps
    List<Map<String, Object>> listConfigMaps(Long clusterId, String namespace);
    Map<String, Object> getConfigMap(Long clusterId, String namespace, String name);
    void createConfigMap(Long clusterId, String namespace, String name, Map<String, String> data);
    void updateConfigMap(Long clusterId, String namespace, String name, Map<String, String> data);
    void deleteConfigMap(Long clusterId, String namespace, String name);

    // Secrets
    List<Map<String, Object>> listSecrets(Long clusterId, String namespace);
    Map<String, Object> getSecret(Long clusterId, String namespace, String name);
    void createSecret(Long clusterId, String namespace, String name, String type, Map<String, String> data);
    void updateSecret(Long clusterId, String namespace, String name, Map<String, String> data);
    void deleteSecret(Long clusterId, String namespace, String name);
}
