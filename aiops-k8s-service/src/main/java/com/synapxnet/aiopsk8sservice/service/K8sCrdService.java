package com.synapxnet.aiopsk8sservice.service;

import java.util.List;
import java.util.Map;

public interface K8sCrdService {
    List<Map<String, Object>> listCrds(Long clusterId);
    Map<String, Object> getCrd(Long clusterId, String name);
    List<Map<String, Object>> listCustomResources(Long clusterId, String group, String version, String plural, String namespace);
    Map<String, Object> getCustomResource(Long clusterId, String group, String version, String plural, String namespace, String name);
    void deleteCustomResource(Long clusterId, String group, String version, String plural, String namespace, String name);
}
