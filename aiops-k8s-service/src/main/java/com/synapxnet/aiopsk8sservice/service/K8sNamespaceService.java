package com.synapxnet.aiopsk8sservice.service;

import java.util.List;
import java.util.Map;

public interface K8sNamespaceService {

    List<Map<String, Object>> listNamespaces(Long clusterId);

    Map<String, Object> getNamespace(Long clusterId, String namespace);

    void createNamespace(Long clusterId, String name, Map<String, String> labels);

    void deleteNamespace(Long clusterId, String namespace);

    Map<String, Object> getNamespaceOverview(Long clusterId, String namespace);

    List<Map<String, Object>> getNamespaceEvents(Long clusterId, String namespace, int limit);

    List<Map<String, Object>> getResourceQuotas(Long clusterId, String namespace);

    void setResourceQuota(Long clusterId, String namespace, Map<String, String> hard);
}
