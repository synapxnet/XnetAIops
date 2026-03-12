package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.service.K8sNetworkPolicyService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/clusters/{clusterId}/namespaces/{namespace}/networkpolicies")
public class K8sNetworkController {

    private final K8sNetworkPolicyService networkPolicyService;

    public K8sNetworkController(K8sNetworkPolicyService networkPolicyService) {
        this.networkPolicyService = networkPolicyService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(@PathVariable Long clusterId, @PathVariable String namespace) {
        return Result.success(networkPolicyService.listNetworkPolicies(clusterId, namespace));
    }

    @GetMapping("/{name}")
    public Result<Map<String, Object>> get(@PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        return Result.success(networkPolicyService.getNetworkPolicy(clusterId, namespace, name));
    }

    @PostMapping
    public Result<Void> create(@PathVariable Long clusterId, @PathVariable String namespace, @RequestBody Map<String, String> body) {
        networkPolicyService.createNetworkPolicyFromYaml(clusterId, namespace, body.get("yaml"));
        return Result.success();
    }

    @DeleteMapping("/{name}")
    public Result<Void> delete(@PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        networkPolicyService.deleteNetworkPolicy(clusterId, namespace, name);
        return Result.success();
    }
}
