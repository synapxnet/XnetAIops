package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.service.K8sCrdService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/clusters/{clusterId}/crds")
public class K8sCrdController {

    private final K8sCrdService crdService;

    public K8sCrdController(K8sCrdService crdService) {
        this.crdService = crdService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> listCrds(@PathVariable Long clusterId) {
        return Result.success(crdService.listCrds(clusterId));
    }

    @GetMapping("/{name}")
    public Result<Map<String, Object>> getCrd(@PathVariable Long clusterId, @PathVariable String name) {
        return Result.success(crdService.getCrd(clusterId, name));
    }

    @GetMapping("/{name}/resources")
    public Result<List<Map<String, Object>>> listCustomResources(
            @PathVariable Long clusterId,
            @PathVariable String name,
            @RequestParam String group,
            @RequestParam String version,
            @RequestParam String plural,
            @RequestParam(required = false) String namespace) {
        return Result.success(crdService.listCustomResources(clusterId, group, version, plural, namespace));
    }

    @GetMapping("/{name}/resources/{resourceName}")
    public Result<Map<String, Object>> getCustomResource(
            @PathVariable Long clusterId,
            @PathVariable String name,
            @PathVariable String resourceName,
            @RequestParam String group,
            @RequestParam String version,
            @RequestParam String plural,
            @RequestParam(required = false) String namespace) {
        return Result.success(crdService.getCustomResource(clusterId, group, version, plural, namespace, resourceName));
    }

    @DeleteMapping("/{name}/resources/{resourceName}")
    public Result<Void> deleteCustomResource(
            @PathVariable Long clusterId,
            @PathVariable String name,
            @PathVariable String resourceName,
            @RequestParam String group,
            @RequestParam String version,
            @RequestParam String plural,
            @RequestParam(required = false) String namespace) {
        crdService.deleteCustomResource(clusterId, group, version, plural, namespace, resourceName);
        return Result.success();
    }
}
