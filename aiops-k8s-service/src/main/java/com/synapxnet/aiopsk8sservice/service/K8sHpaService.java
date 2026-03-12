package com.synapxnet.aiopsk8sservice.service;

import java.util.List;
import java.util.Map;

public interface K8sHpaService {
    List<Map<String, Object>> listHpas(Long clusterId, String namespace);
    Map<String, Object> getHpa(Long clusterId, String namespace, String name);
    void createHpaFromYaml(Long clusterId, String namespace, String yaml);
    void updateHpaFromYaml(Long clusterId, String namespace, String name, String yaml);
    void deleteHpa(Long clusterId, String namespace, String name);
}
