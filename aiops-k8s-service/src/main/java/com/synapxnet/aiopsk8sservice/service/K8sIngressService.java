package com.synapxnet.aiopsk8sservice.service;

import java.util.List;
import java.util.Map;

public interface K8sIngressService {

    List<Map<String, Object>> listIngresses(Long clusterId, String namespace);

    Map<String, Object> getIngress(Long clusterId, String namespace, String name);

    void createIngress(Long clusterId, String namespace, String yaml);

    void updateIngress(Long clusterId, String namespace, String name, String yaml);

    void deleteIngress(Long clusterId, String namespace, String name);
}
