package com.synapxnet.aiopsk8sservice.service;

import java.util.List;
import java.util.Map;

public interface K8sMonitoringService {

    // Cluster-level metrics
    Map<String, Object> getClusterMetrics(Long clusterId, long start, long end, String step);
    Map<String, Object> getClusterStatus(Long clusterId);

    // Node-level metrics
    Map<String, Object> getNodeMetrics(Long clusterId, String nodeName, long start, long end, String step);
    List<Map<String, Object>> getNodeRanking(Long clusterId, String metric, int topN);

    // Namespace-level metrics
    Map<String, Object> getNamespaceMetrics(Long clusterId, String namespace, long start, long end, String step);
    List<Map<String, Object>> getNamespaceRanking(Long clusterId, String metric, int topN);

    // Workload-level metrics
    Map<String, Object> getWorkloadMetrics(Long clusterId, String namespace, String workload, long start, long end, String step);

    // Pod-level metrics
    Map<String, Object> getPodMetrics(Long clusterId, String namespace, String podName, long start, long end, String step);

    // Component metrics
    Map<String, Object> getApiServerMetrics(Long clusterId, long start, long end, String step);
    Map<String, Object> getEtcdMetrics(Long clusterId, long start, long end, String step);
    Map<String, Object> getSchedulerMetrics(Long clusterId, long start, long end, String step);

    // Custom PromQL
    List<Map<String, Object>> customQuery(Long clusterId, String promql);
    List<Map<String, Object>> customQueryRange(Long clusterId, String promql, long start, long end, String step);

    // Prometheus config management
    Map<String, Object> getPrometheusConfig(Long clusterId);
    void savePrometheusConfig(Long clusterId, Map<String, String> config);
    boolean testPrometheusConnection(Long clusterId);
}
