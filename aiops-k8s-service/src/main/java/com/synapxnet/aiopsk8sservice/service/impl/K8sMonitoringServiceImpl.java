package com.synapxnet.aiopsk8sservice.service.impl;

import com.synapxnet.aiopsk8sservice.entity.K8sPrometheusConfig;
import com.synapxnet.aiopsk8sservice.mapper.K8sPrometheusConfigMapper;
import com.synapxnet.aiopsk8sservice.service.K8sMonitoringService;
import com.synapxnet.aiopsk8sservice.service.PrometheusQueryService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class K8sMonitoringServiceImpl implements K8sMonitoringService {

    private final PrometheusQueryService promService;
    private final K8sPrometheusConfigMapper configMapper;

    public K8sMonitoringServiceImpl(PrometheusQueryService promService, K8sPrometheusConfigMapper configMapper) {
        this.promService = promService;
        this.configMapper = configMapper;
    }

    @Override
    public Map<String, Object> getClusterMetrics(Long clusterId, long start, long end, String step) {
        Map<String, Object> result = new HashMap<>();
        result.put("cpuUsage", promService.queryRangeAsList(clusterId,
                "1 - avg(rate(node_cpu_seconds_total{mode=\"idle\"}[5m]))", start, end, step));
        result.put("memoryUsage", promService.queryRangeAsList(clusterId,
                "1 - sum(node_memory_MemAvailable_bytes) / sum(node_memory_MemTotal_bytes)", start, end, step));
        result.put("diskUsage", promService.queryRangeAsList(clusterId,
                "1 - sum(node_filesystem_avail_bytes{mountpoint=\"/\"}) / sum(node_filesystem_size_bytes{mountpoint=\"/\"})", start, end, step));
        result.put("networkReceive", promService.queryRangeAsList(clusterId,
                "sum(rate(node_network_receive_bytes_total{device!~\"lo|veth.*|docker.*|br.*\"}[5m]))", start, end, step));
        result.put("networkTransmit", promService.queryRangeAsList(clusterId,
                "sum(rate(node_network_transmit_bytes_total{device!~\"lo|veth.*|docker.*|br.*\"}[5m]))", start, end, step));
        result.put("podCount", promService.queryRangeAsList(clusterId,
                "sum(kubelet_running_pods)", start, end, step));
        return result;
    }

    @Override
    public Map<String, Object> getClusterStatus(Long clusterId) {
        Map<String, Object> result = new HashMap<>();
        // Current CPU usage
        result.put("cpuUsage", promService.queryScalar(clusterId,
                "1 - avg(rate(node_cpu_seconds_total{mode=\"idle\"}[5m]))"));
        result.put("cpuTotal", promService.queryScalar(clusterId,
                "count(node_cpu_seconds_total{mode=\"idle\"})"));
        // Current Memory
        result.put("memoryUsed", promService.queryScalar(clusterId,
                "sum(node_memory_MemTotal_bytes - node_memory_MemAvailable_bytes)"));
        result.put("memoryTotal", promService.queryScalar(clusterId,
                "sum(node_memory_MemTotal_bytes)"));
        // Disk
        result.put("diskUsed", promService.queryScalar(clusterId,
                "sum(node_filesystem_size_bytes{mountpoint=\"/\"} - node_filesystem_avail_bytes{mountpoint=\"/\"})"));
        result.put("diskTotal", promService.queryScalar(clusterId,
                "sum(node_filesystem_size_bytes{mountpoint=\"/\"})"));
        // Pod count
        result.put("podRunning", promService.queryScalar(clusterId,
                "sum(kubelet_running_pods)"));
        result.put("podTotal", promService.queryScalar(clusterId,
                "sum(kube_node_status_allocatable{resource=\"pods\"})"));
        // Node status
        result.put("nodeReady", promService.queryScalar(clusterId,
                "sum(kube_node_status_condition{condition=\"Ready\",status=\"true\"})"));
        result.put("nodeTotal", promService.queryScalar(clusterId,
                "count(kube_node_info)"));
        return result;
    }

    @Override
    public Map<String, Object> getNodeMetrics(Long clusterId, String nodeName, long start, long end, String step) {
        Map<String, Object> result = new HashMap<>();
        String nodeFilter = "instance=~\"" + nodeName + ".*\"";
        result.put("cpuUsage", promService.queryRangeAsList(clusterId,
                "1 - avg(rate(node_cpu_seconds_total{mode=\"idle\"," + nodeFilter + "}[5m]))", start, end, step));
        result.put("memoryUsage", promService.queryRangeAsList(clusterId,
                "1 - (node_memory_MemAvailable_bytes{" + nodeFilter + "} / node_memory_MemTotal_bytes{" + nodeFilter + "})", start, end, step));
        result.put("diskIO", promService.queryRangeAsList(clusterId,
                "rate(node_disk_io_time_seconds_total{" + nodeFilter + "}[5m])", start, end, step));
        result.put("networkReceive", promService.queryRangeAsList(clusterId,
                "rate(node_network_receive_bytes_total{" + nodeFilter + ",device!~\"lo|veth.*\"}[5m])", start, end, step));
        result.put("networkTransmit", promService.queryRangeAsList(clusterId,
                "rate(node_network_transmit_bytes_total{" + nodeFilter + ",device!~\"lo|veth.*\"}[5m])", start, end, step));
        result.put("load1", promService.queryRangeAsList(clusterId,
                "node_load1{" + nodeFilter + "}", start, end, step));
        result.put("load5", promService.queryRangeAsList(clusterId,
                "node_load5{" + nodeFilter + "}", start, end, step));
        result.put("load15", promService.queryRangeAsList(clusterId,
                "node_load15{" + nodeFilter + "}", start, end, step));
        return result;
    }

    @Override
    public List<Map<String, Object>> getNodeRanking(Long clusterId, String metric, int topN) {
        String promql;
        switch (metric) {
            case "cpu":
                promql = "topk(" + topN + ", 1 - avg by(instance)(rate(node_cpu_seconds_total{mode=\"idle\"}[5m])))";
                break;
            case "memory":
                promql = "topk(" + topN + ", 1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes))";
                break;
            case "disk":
                promql = "topk(" + topN + ", 1 - (node_filesystem_avail_bytes{mountpoint=\"/\"} / node_filesystem_size_bytes{mountpoint=\"/\"}))";
                break;
            case "load":
                promql = "topk(" + topN + ", node_load5)";
                break;
            case "pod":
                promql = "topk(" + topN + ", kubelet_running_pods)";
                break;
            default:
                promql = "topk(" + topN + ", 1 - avg by(instance)(rate(node_cpu_seconds_total{mode=\"idle\"}[5m])))";
        }
        return promService.queryVector(clusterId, promql);
    }

    @Override
    public Map<String, Object> getNamespaceMetrics(Long clusterId, String namespace, long start, long end, String step) {
        Map<String, Object> result = new HashMap<>();
        result.put("cpuUsage", promService.queryRangeAsList(clusterId,
                "sum(rate(container_cpu_usage_seconds_total{namespace=\"" + namespace + "\"}[5m]))", start, end, step));
        result.put("memoryUsage", promService.queryRangeAsList(clusterId,
                "sum(container_memory_working_set_bytes{namespace=\"" + namespace + "\"})", start, end, step));
        result.put("networkReceive", promService.queryRangeAsList(clusterId,
                "sum(rate(container_network_receive_bytes_total{namespace=\"" + namespace + "\"}[5m]))", start, end, step));
        result.put("networkTransmit", promService.queryRangeAsList(clusterId,
                "sum(rate(container_network_transmit_bytes_total{namespace=\"" + namespace + "\"}[5m]))", start, end, step));
        return result;
    }

    @Override
    public List<Map<String, Object>> getNamespaceRanking(Long clusterId, String metric, int topN) {
        String promql;
        switch (metric) {
            case "cpu":
                promql = "topk(" + topN + ", sum by(namespace)(rate(container_cpu_usage_seconds_total[5m])))";
                break;
            case "memory":
                promql = "topk(" + topN + ", sum by(namespace)(container_memory_working_set_bytes))";
                break;
            default:
                promql = "topk(" + topN + ", sum by(namespace)(rate(container_cpu_usage_seconds_total[5m])))";
        }
        return promService.queryVector(clusterId, promql);
    }

    @Override
    public Map<String, Object> getWorkloadMetrics(Long clusterId, String namespace, String workload, long start, long end, String step) {
        Map<String, Object> result = new HashMap<>();
        String filter = "namespace=\"" + namespace + "\",pod=~\"" + workload + ".*\"";
        result.put("cpuUsage", promService.queryRangeAsList(clusterId,
                "sum(rate(container_cpu_usage_seconds_total{" + filter + "}[5m]))", start, end, step));
        result.put("memoryUsage", promService.queryRangeAsList(clusterId,
                "sum(container_memory_working_set_bytes{" + filter + "})", start, end, step));
        return result;
    }

    @Override
    public Map<String, Object> getPodMetrics(Long clusterId, String namespace, String podName, long start, long end, String step) {
        Map<String, Object> result = new HashMap<>();
        String filter = "namespace=\"" + namespace + "\",pod=\"" + podName + "\"";
        result.put("cpuUsage", promService.queryRangeAsList(clusterId,
                "sum(rate(container_cpu_usage_seconds_total{" + filter + "}[5m])) by (container)", start, end, step));
        result.put("memoryUsage", promService.queryRangeAsList(clusterId,
                "sum(container_memory_working_set_bytes{" + filter + "}) by (container)", start, end, step));
        result.put("networkReceive", promService.queryRangeAsList(clusterId,
                "sum(rate(container_network_receive_bytes_total{" + filter + "}[5m]))", start, end, step));
        result.put("networkTransmit", promService.queryRangeAsList(clusterId,
                "sum(rate(container_network_transmit_bytes_total{" + filter + "}[5m]))", start, end, step));
        return result;
    }

    @Override
    public Map<String, Object> getApiServerMetrics(Long clusterId, long start, long end, String step) {
        Map<String, Object> result = new HashMap<>();
        result.put("requestRate", promService.queryRangeAsList(clusterId,
                "sum(rate(apiserver_request_total[5m])) by (code)", start, end, step));
        result.put("requestLatency", promService.queryRangeAsList(clusterId,
                "histogram_quantile(0.99, sum(rate(apiserver_request_duration_seconds_bucket[5m])) by (le, verb))", start, end, step));
        result.put("requestDuration", promService.queryRangeAsList(clusterId,
                "sum(rate(apiserver_request_duration_seconds_sum[5m])) / sum(rate(apiserver_request_duration_seconds_count[5m]))", start, end, step));
        result.put("currentInflight", promService.queryRangeAsList(clusterId,
                "sum(apiserver_current_inflight_requests) by (request_kind)", start, end, step));
        return result;
    }

    @Override
    public Map<String, Object> getEtcdMetrics(Long clusterId, long start, long end, String step) {
        Map<String, Object> result = new HashMap<>();
        result.put("dbSize", promService.queryRangeAsList(clusterId,
                "etcd_mvcc_db_total_size_in_bytes", start, end, step));
        result.put("walFsyncDuration", promService.queryRangeAsList(clusterId,
                "histogram_quantile(0.99, sum(rate(etcd_disk_wal_fsync_duration_seconds_bucket[5m])) by (le))", start, end, step));
        result.put("backendCommitDuration", promService.queryRangeAsList(clusterId,
                "histogram_quantile(0.99, sum(rate(etcd_disk_backend_commit_duration_seconds_bucket[5m])) by (le))", start, end, step));
        result.put("leaderChanges", promService.queryRangeAsList(clusterId,
                "etcd_server_leader_changes_seen_total", start, end, step));
        result.put("proposals", promService.queryRangeAsList(clusterId,
                "sum(rate(etcd_server_proposals_committed_total[5m]))", start, end, step));
        result.put("grpcRequestRate", promService.queryRangeAsList(clusterId,
                "sum(rate(grpc_server_started_total{grpc_type=\"unary\"}[5m]))", start, end, step));
        return result;
    }

    @Override
    public Map<String, Object> getSchedulerMetrics(Long clusterId, long start, long end, String step) {
        Map<String, Object> result = new HashMap<>();
        result.put("schedulingRate", promService.queryRangeAsList(clusterId,
                "sum(rate(scheduler_schedule_attempts_total[5m])) by (result)", start, end, step));
        result.put("schedulingLatency", promService.queryRangeAsList(clusterId,
                "histogram_quantile(0.99, sum(rate(scheduler_scheduling_attempt_duration_seconds_bucket[5m])) by (le))", start, end, step));
        result.put("pendingPods", promService.queryRangeAsList(clusterId,
                "scheduler_pending_pods", start, end, step));
        return result;
    }

    @Override
    public List<Map<String, Object>> customQuery(Long clusterId, String promql) {
        return promService.queryVector(clusterId, promql);
    }

    @Override
    public List<Map<String, Object>> customQueryRange(Long clusterId, String promql, long start, long end, String step) {
        return promService.queryRangeAsList(clusterId, promql, start, end, step);
    }

    @Override
    public Map<String, Object> getPrometheusConfig(Long clusterId) {
        K8sPrometheusConfig config = configMapper.findByClusterId(clusterId);
        if (config == null) return null;
        Map<String, Object> result = new HashMap<>();
        result.put("id", config.getId());
        result.put("clusterId", config.getClusterId());
        result.put("prometheusUrl", config.getPrometheusUrl());
        result.put("authType", config.getAuthType());
        result.put("status", config.getStatus());
        result.put("createdAt", config.getCreatedAt());
        return result;
    }

    @Override
    public void savePrometheusConfig(Long clusterId, Map<String, String> configMap) {
        K8sPrometheusConfig existing = configMapper.findByClusterId(clusterId);
        K8sPrometheusConfig config = existing != null ? existing : new K8sPrometheusConfig();
        config.setClusterId(clusterId);
        config.setPrometheusUrl(configMap.get("prometheusUrl"));
        config.setAuthType(configMap.getOrDefault("authType", "none"));
        config.setAuthToken(configMap.get("authToken"));
        config.setUsername(configMap.get("username"));
        config.setPassword(configMap.get("password"));
        config.setStatus("active");

        if (existing != null) {
            configMapper.updateByClusterId(config);
        } else {
            configMapper.insert(config);
        }
    }

    @Override
    public boolean testPrometheusConnection(Long clusterId) {
        return promService.testConnection(clusterId);
    }
}
