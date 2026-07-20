package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.entity.K8sDeployLog;
import com.synapxnet.aiopsk8sservice.entity.K8sDeployNode;
import com.synapxnet.aiopsk8sservice.entity.K8sDeployPlan;
import com.synapxnet.aiopsk8sservice.service.K8sDeployService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/deploy")
public class K8sDeployController {

    private final K8sDeployService deployService;

    public K8sDeployController(K8sDeployService deployService) {
        this.deployService = deployService;
    }

    @GetMapping("/plans")
    public Result<List<K8sDeployPlan>> listPlans() {
        return Result.success(deployService.listPlans());
    }

    @GetMapping("/plans/{id}")
    public Result<Map<String, Object>> getPlanDetail(@PathVariable Long id) {
        return Result.success(deployService.getPlanDetail(id));
    }

    @PostMapping("/plans")
    public Result<K8sDeployPlan> createPlan(@RequestBody Map<String, Object> body) {
        K8sDeployPlan plan = new K8sDeployPlan();
        plan.setPlanName((String) body.get("planName"));
        plan.setK8sVersion((String) body.get("k8sVersion"));
        plan.setDeployType((String) body.getOrDefault("deployType", "single"));
        plan.setNetworkPlugin((String) body.getOrDefault("networkPlugin", "calico"));
        plan.setContainerRuntime((String) body.getOrDefault("containerRuntime", "containerd"));
        plan.setPodCidr((String) body.getOrDefault("podCidr", "10.244.0.0/16"));
        plan.setServiceCidr((String) body.getOrDefault("serviceCidr", "10.96.0.0/12"));
        plan.setInstallMetricsServer((Boolean) body.getOrDefault("installMetricsServer", true));
        plan.setInstallIngressNginx((Boolean) body.getOrDefault("installIngressNginx", false));
        plan.setStoragePlugin((String) body.getOrDefault("storagePlugin", "local-path"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodesData = (List<Map<String, Object>>) body.get("nodes");
        List<K8sDeployNode> nodes = new java.util.ArrayList<>();
        if (nodesData != null) {
            for (Map<String, Object> nd : nodesData) {
                K8sDeployNode node = new K8sDeployNode();
                node.setHost((String) nd.get("host"));
                node.setSshPort(nd.get("sshPort") != null ? ((Number) nd.get("sshPort")).intValue() : 22);
                node.setSshUser((String) nd.getOrDefault("sshUser", "root"));
                node.setSshPassword((String) nd.get("sshPassword"));
                node.setSshKey((String) nd.get("sshKey"));
                node.setRole((String) nd.get("role"));
                node.setHostname((String) nd.get("hostname"));
                nodes.add(node);
            }
        }

        return Result.success(deployService.createPlan(plan, nodes));
    }

    @DeleteMapping("/plans/{id}")
    public Result<Void> deletePlan(@PathVariable Long id) {
        deployService.deletePlan(id);
        return Result.success();
    }

    @PostMapping("/plans/{id}/validate")
    public Result<Map<String, Object>> validatePlan(@PathVariable Long id) {
        Map<String, Object> detail = deployService.getPlanDetail(id);
        if (detail == null) return Result.error("Plan not found");

        @SuppressWarnings("unchecked")
        List<K8sDeployNode> nodes = (List<K8sDeployNode>) detail.get("nodes");
        int validCount = 0;
        for (K8sDeployNode node : nodes) {
            if (deployService.validateNode(node.getId())) validCount++;
        }

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("total", nodes.size());
        result.put("valid", validCount);
        result.put("allValid", validCount == nodes.size());
        return Result.success(result);
    }

    @PostMapping("/plans/{id}/execute")
    public Result<Void> executePlan(@PathVariable Long id) {
        deployService.executePlan(id);
        return Result.success();
    }

    @GetMapping("/plans/{id}/logs")
    public Result<List<K8sDeployLog>> getLogs(@PathVariable Long id) {
        return Result.success(deployService.getLogs(id));
    }

    @PostMapping("/plans/{planId}/add-node")
    public Result<Void> addNode(@PathVariable Long planId, @RequestBody K8sDeployNode node) {
        // Check plan status: if completed, trigger scale-out with full join flow
        Map<String, Object> detail = deployService.getPlanDetail(planId);
        if (detail != null) {
            K8sDeployPlan plan = (K8sDeployPlan) detail.get("plan");
            if (plan != null && "completed".equals(plan.getStatus())) {
                deployService.scaleOutNode(planId, node);
                return Result.success();
            }
        }
        deployService.addNode(planId, node);
        return Result.success();
    }

    @DeleteMapping("/plans/{planId}/nodes/{nodeId}")
    public Result<Void> removeNode(@PathVariable Long nodeId) {
        deployService.removeNode(nodeId);
        return Result.success();
    }

    @GetMapping("/supported-versions")
    public Result<List<String>> getSupportedVersions() {
        return Result.success(deployService.getSupportedVersions());
    }

    @GetMapping("/supported-cni")
    public Result<List<Map<String, String>>> getSupportedCni() {
        return Result.success(deployService.getSupportedCni());
    }

    @GetMapping("/supported-cri")
    public Result<List<Map<String, String>>> getSupportedCri() {
        return Result.success(deployService.getSupportedCri());
    }

    @GetMapping("/supported-storage")
    public Result<List<Map<String, String>>> getSupportedStorage() {
        return Result.success(deployService.getSupportedStorage());
    }
}
