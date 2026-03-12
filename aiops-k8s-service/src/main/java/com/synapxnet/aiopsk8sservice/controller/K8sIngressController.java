package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.service.K8sIngressService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/clusters/{clusterId}/namespaces/{namespace}/ingresses")
public class K8sIngressController {

    private final K8sIngressService ingressService;

    public K8sIngressController(K8sIngressService ingressService) {
        this.ingressService = ingressService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> listIngresses(
            @PathVariable Long clusterId, @PathVariable String namespace) {
        return Result.success(ingressService.listIngresses(clusterId, namespace));
    }

    @GetMapping("/{name}")
    public Result<Map<String, Object>> getIngress(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        return Result.success(ingressService.getIngress(clusterId, namespace, name));
    }

    @PostMapping
    public Result<Void> createIngress(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @RequestBody Map<String, String> body) {
        ingressService.createIngress(clusterId, namespace, body.get("yaml"));
        return Result.success();
    }

    @PutMapping("/{name}")
    public Result<Void> updateIngress(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @PathVariable String name, @RequestBody Map<String, String> body) {
        ingressService.updateIngress(clusterId, namespace, name, body.get("yaml"));
        return Result.success();
    }

    @DeleteMapping("/{name}")
    public Result<Void> deleteIngress(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        ingressService.deleteIngress(clusterId, namespace, name);
        return Result.success();
    }
}
