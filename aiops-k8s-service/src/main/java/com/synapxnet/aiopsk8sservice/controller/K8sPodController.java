package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.service.K8sPodService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/clusters/{clusterId}/namespaces/{namespace}/pods")
public class K8sPodController {

    private final K8sPodService podService;

    public K8sPodController(K8sPodService podService) {
        this.podService = podService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> listPods(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @RequestParam(required = false) Map<String, String> labels) {
        // Filter out path variables from the params
        Map<String, String> labelSelector = new HashMap<>();
        if (labels != null) {
            labels.forEach((k, v) -> {
                if (!k.equals("clusterId") && !k.equals("namespace")) {
                    labelSelector.put(k, v);
                }
            });
        }
        return Result.success(podService.listPods(clusterId, namespace, labelSelector.isEmpty() ? null : labelSelector));
    }

    @GetMapping("/{podName}")
    public Result<Map<String, Object>> getPod(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String podName) {
        return Result.success(podService.getPod(clusterId, namespace, podName));
    }

    @DeleteMapping("/{podName}")
    public Result<Void> deletePod(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String podName) {
        podService.deletePod(clusterId, namespace, podName);
        return Result.success();
    }

    @GetMapping("/{podName}/logs")
    public Result<String> getPodLogs(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String podName,
            @RequestParam(required = false) String container,
            @RequestParam(required = false, defaultValue = "1000") Integer tailLines) {
        return Result.success(podService.getPodLogs(clusterId, namespace, podName, container, tailLines));
    }

    @GetMapping("/{podName}/events")
    public Result<List<Map<String, Object>>> getPodEvents(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String podName) {
        return Result.success(podService.getPodEvents(clusterId, namespace, podName));
    }

    @GetMapping("/{podName}/containers")
    public Result<List<Map<String, Object>>> getPodContainers(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String podName) {
        return Result.success(podService.getPodContainers(clusterId, namespace, podName));
    }
}
