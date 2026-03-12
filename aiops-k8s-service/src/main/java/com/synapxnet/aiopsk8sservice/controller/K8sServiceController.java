package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.service.K8sServiceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/clusters/{clusterId}/namespaces/{namespace}/services")
public class K8sServiceController {

    private final K8sServiceService serviceService;

    public K8sServiceController(K8sServiceService serviceService) {
        this.serviceService = serviceService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> listServices(
            @PathVariable Long clusterId, @PathVariable String namespace) {
        return Result.success(serviceService.listServices(clusterId, namespace));
    }

    @GetMapping("/{name}")
    public Result<Map<String, Object>> getService(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        return Result.success(serviceService.getService(clusterId, namespace, name));
    }

    @PostMapping
    public Result<Void> createService(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @RequestBody Map<String, String> body) {
        serviceService.createService(clusterId, namespace, body.get("yaml"));
        return Result.success();
    }

    @PutMapping("/{name}")
    public Result<Void> updateService(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @PathVariable String name, @RequestBody Map<String, String> body) {
        serviceService.updateService(clusterId, namespace, name, body.get("yaml"));
        return Result.success();
    }

    @DeleteMapping("/{name}")
    public Result<Void> deleteService(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        serviceService.deleteService(clusterId, namespace, name);
        return Result.success();
    }

    @GetMapping("/{name}/endpoints")
    public Result<List<Map<String, Object>>> getEndpoints(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        return Result.success(serviceService.getEndpoints(clusterId, namespace, name));
    }
}
