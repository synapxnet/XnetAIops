package com.synapxnet.aiopsk8sservice.service;

import java.util.List;
import java.util.Map;

public interface K8sServiceService {

    List<Map<String, Object>> listServices(Long clusterId, String namespace);

    Map<String, Object> getService(Long clusterId, String namespace, String name);

    void createService(Long clusterId, String namespace, String yaml);

    void updateService(Long clusterId, String namespace, String name, String yaml);

    void deleteService(Long clusterId, String namespace, String name);

    List<Map<String, Object>> getEndpoints(Long clusterId, String namespace, String name);
}
