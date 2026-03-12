package com.synapxnet.aiopsk8sservice.service;

import java.util.List;
import java.util.Map;

public interface K8sWorkloadService {

    // Deployments
    List<Map<String, Object>> listDeployments(Long clusterId, String namespace);
    Map<String, Object> getDeployment(Long clusterId, String namespace, String name);
    void createDeployment(Long clusterId, String namespace, String yaml);
    void updateDeployment(Long clusterId, String namespace, String name, String yaml);
    void deleteDeployment(Long clusterId, String namespace, String name);
    void scaleDeployment(Long clusterId, String namespace, String name, int replicas);
    void restartDeployment(Long clusterId, String namespace, String name);
    List<Map<String, Object>> getDeploymentRevisions(Long clusterId, String namespace, String name);
    void rollbackDeployment(Long clusterId, String namespace, String name, Long revision);

    // StatefulSets
    List<Map<String, Object>> listStatefulSets(Long clusterId, String namespace);
    Map<String, Object> getStatefulSet(Long clusterId, String namespace, String name);
    void createStatefulSet(Long clusterId, String namespace, String yaml);
    void updateStatefulSet(Long clusterId, String namespace, String name, String yaml);
    void deleteStatefulSet(Long clusterId, String namespace, String name);
    void scaleStatefulSet(Long clusterId, String namespace, String name, int replicas);
    void restartStatefulSet(Long clusterId, String namespace, String name);

    // DaemonSets
    List<Map<String, Object>> listDaemonSets(Long clusterId, String namespace);
    Map<String, Object> getDaemonSet(Long clusterId, String namespace, String name);
    void createDaemonSet(Long clusterId, String namespace, String yaml);
    void updateDaemonSet(Long clusterId, String namespace, String name, String yaml);
    void deleteDaemonSet(Long clusterId, String namespace, String name);
    void restartDaemonSet(Long clusterId, String namespace, String name);
}
