package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.service.K8sNamespaceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/clusters/{clusterId}/namespaces")
public class K8sNamespaceController {

    private final K8sNamespaceService namespaceService;

    public K8sNamespaceController(K8sNamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> listNamespaces(@PathVariable Long clusterId) {
        return Result.success(namespaceService.listNamespaces(clusterId));
    }

    @GetMapping("/{namespace}")
    public Result<Map<String, Object>> getNamespace(
            @PathVariable Long clusterId, @PathVariable String namespace) {
        return Result.success(namespaceService.getNamespace(clusterId, namespace));
    }

    @PostMapping
    public Result<Void> createNamespace(
            @PathVariable Long clusterId, @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        @SuppressWarnings("unchecked")
        Map<String, String> labels = (Map<String, String>) body.get("labels");
        namespaceService.createNamespace(clusterId, name, labels);
        return Result.success();
    }

    @DeleteMapping("/{namespace}")
    public Result<Void> deleteNamespace(
            @PathVariable Long clusterId, @PathVariable String namespace) {
        namespaceService.deleteNamespace(clusterId, namespace);
        return Result.success();
    }

    @GetMapping("/{namespace}/overview")
    public Result<Map<String, Object>> getNamespaceOverview(
            @PathVariable Long clusterId, @PathVariable String namespace) {
        return Result.success(namespaceService.getNamespaceOverview(clusterId, namespace));
    }

    @GetMapping("/{namespace}/events")
    public Result<List<Map<String, Object>>> getNamespaceEvents(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(namespaceService.getNamespaceEvents(clusterId, namespace, limit));
    }

    @GetMapping("/{namespace}/resource-quotas")
    public Result<List<Map<String, Object>>> getResourceQuotas(
            @PathVariable Long clusterId, @PathVariable String namespace) {
        return Result.success(namespaceService.getResourceQuotas(clusterId, namespace));
    }

    @PostMapping("/{namespace}/resource-quotas")
    public Result<Void> setResourceQuota(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @RequestBody Map<String, String> hard) {
        namespaceService.setResourceQuota(clusterId, namespace, hard);
        return Result.success();
    }
}
