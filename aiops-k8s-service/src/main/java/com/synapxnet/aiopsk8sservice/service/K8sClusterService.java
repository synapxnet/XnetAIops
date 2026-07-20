package com.synapxnet.aiopsk8sservice.service;

import com.synapxnet.aiopsk8sservice.entity.K8sCluster;
import com.synapxnet.aiopsk8sservice.entity.K8sClusterComponent;
import com.synapxnet.aiopsk8sservice.entity.K8sClusterMetricsSnapshot;

import java.util.List;
import java.util.Map;

public interface K8sClusterService {

    List<K8sCluster> listAll();

    K8sCluster getById(Long id);

    K8sCluster create(K8sCluster cluster, String rawKubeconfig);

    K8sCluster update(Long id, K8sCluster cluster, String rawKubeconfig);

    void delete(Long id);

    void updateSsh(K8sCluster cluster);

    Map<String, Object> testConnection(String kubeconfigContent);

    Map<String, Object> getOverview(Long id);

    List<K8sClusterComponent> getComponents(Long id);

    K8sClusterMetricsSnapshot getMetrics(Long id);

    List<Map<String, Object>> getEvents(Long id, int limit);

    String getKubeconfig(Long id);
}
