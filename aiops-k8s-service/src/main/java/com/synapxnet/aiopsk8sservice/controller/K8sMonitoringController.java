package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.service.K8sMonitoringService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/clusters/{clusterId}/monitoring")
public class K8sMonitoringController {

    private final K8sMonitoringService monitoringService;

    public K8sMonitoringController(K8sMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping("/cluster")
    public Result<Map<String, Object>> getClusterMetrics(
            @PathVariable Long clusterId,
            @RequestParam long start,
            @RequestParam long end,
            @RequestParam(defaultValue = "60") String step) {
        return Result.success(monitoringService.getClusterMetrics(clusterId, start, end, step));
    }

    @GetMapping("/cluster/status")
    public Result<Map<String, Object>> getClusterStatus(@PathVariable Long clusterId) {
        return Result.success(monitoringService.getClusterStatus(clusterId));
    }

    @GetMapping("/nodes/{nodeName}")
    public Result<Map<String, Object>> getNodeMetrics(
            @PathVariable Long clusterId,
            @PathVariable String nodeName,
            @RequestParam long start,
            @RequestParam long end,
            @RequestParam(defaultValue = "60") String step) {
        return Result.success(monitoringService.getNodeMetrics(clusterId, nodeName, start, end, step));
    }

    @GetMapping("/node-ranking")
    public Result<List<Map<String, Object>>> getNodeRanking(
            @PathVariable Long clusterId,
            @RequestParam(defaultValue = "cpu") String metric,
            @RequestParam(defaultValue = "5") int topN) {
        return Result.success(monitoringService.getNodeRanking(clusterId, metric, topN));
    }

    @GetMapping("/namespaces/{namespace}")
    public Result<Map<String, Object>> getNamespaceMetrics(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @RequestParam long start,
            @RequestParam long end,
            @RequestParam(defaultValue = "60") String step) {
        return Result.success(monitoringService.getNamespaceMetrics(clusterId, namespace, start, end, step));
    }

    @GetMapping("/namespace-ranking")
    public Result<List<Map<String, Object>>> getNamespaceRanking(
            @PathVariable Long clusterId,
            @RequestParam(defaultValue = "cpu") String metric,
            @RequestParam(defaultValue = "5") int topN) {
        return Result.success(monitoringService.getNamespaceRanking(clusterId, metric, topN));
    }

    @GetMapping("/namespaces/{namespace}/workloads/{workload}")
    public Result<Map<String, Object>> getWorkloadMetrics(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String workload,
            @RequestParam long start,
            @RequestParam long end,
            @RequestParam(defaultValue = "60") String step) {
        return Result.success(monitoringService.getWorkloadMetrics(clusterId, namespace, workload, start, end, step));
    }

    @GetMapping("/namespaces/{namespace}/pods/{podName}")
    public Result<Map<String, Object>> getPodMetrics(
            @PathVariable Long clusterId,
            @PathVariable String namespace,
            @PathVariable String podName,
            @RequestParam long start,
            @RequestParam long end,
            @RequestParam(defaultValue = "60") String step) {
        return Result.success(monitoringService.getPodMetrics(clusterId, namespace, podName, start, end, step));
    }

    @GetMapping("/api-server")
    public Result<Map<String, Object>> getApiServerMetrics(
            @PathVariable Long clusterId,
            @RequestParam long start,
            @RequestParam long end,
            @RequestParam(defaultValue = "60") String step) {
        return Result.success(monitoringService.getApiServerMetrics(clusterId, start, end, step));
    }

    @GetMapping("/etcd")
    public Result<Map<String, Object>> getEtcdMetrics(
            @PathVariable Long clusterId,
            @RequestParam long start,
            @RequestParam long end,
            @RequestParam(defaultValue = "60") String step) {
        return Result.success(monitoringService.getEtcdMetrics(clusterId, start, end, step));
    }

    @GetMapping("/scheduler")
    public Result<Map<String, Object>> getSchedulerMetrics(
            @PathVariable Long clusterId,
            @RequestParam long start,
            @RequestParam long end,
            @RequestParam(defaultValue = "60") String step) {
        return Result.success(monitoringService.getSchedulerMetrics(clusterId, start, end, step));
    }

    @GetMapping("/query")
    public Result<List<Map<String, Object>>> customQuery(
            @PathVariable Long clusterId,
            @RequestParam String promql) {
        return Result.success(monitoringService.customQuery(clusterId, promql));
    }

    @GetMapping("/query-range")
    public Result<List<Map<String, Object>>> customQueryRange(
            @PathVariable Long clusterId,
            @RequestParam String promql,
            @RequestParam long start,
            @RequestParam long end,
            @RequestParam(defaultValue = "60") String step) {
        return Result.success(monitoringService.customQueryRange(clusterId, promql, start, end, step));
    }

    // Prometheus config management
    @GetMapping("/config")
    public Result<Map<String, Object>> getPrometheusConfig(@PathVariable Long clusterId) {
        return Result.success(monitoringService.getPrometheusConfig(clusterId));
    }

    @PostMapping("/config")
    public Result<Void> savePrometheusConfig(@PathVariable Long clusterId, @RequestBody Map<String, String> config) {
        monitoringService.savePrometheusConfig(clusterId, config);
        return Result.success();
    }

    @PostMapping("/config/test")
    public Result<Boolean> testConnection(@PathVariable Long clusterId) {
        return Result.success(monitoringService.testPrometheusConnection(clusterId));
    }
}
