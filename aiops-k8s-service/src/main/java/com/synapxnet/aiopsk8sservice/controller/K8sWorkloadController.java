package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.service.K8sWorkloadService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/clusters/{clusterId}/namespaces/{namespace}/workloads")
public class K8sWorkloadController {

    private final K8sWorkloadService workloadService;

    public K8sWorkloadController(K8sWorkloadService workloadService) {
        this.workloadService = workloadService;
    }

    // ====== Deployments ======

    @GetMapping("/deployments")
    public Result<List<Map<String, Object>>> listDeployments(
            @PathVariable Long clusterId, @PathVariable String namespace) {
        return Result.success(workloadService.listDeployments(clusterId, namespace));
    }

    @GetMapping("/deployments/{name}")
    public Result<Map<String, Object>> getDeployment(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        return Result.success(workloadService.getDeployment(clusterId, namespace, name));
    }

    @PostMapping("/deployments")
    public Result<Void> createDeployment(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @RequestBody Map<String, String> body) {
        workloadService.createDeployment(clusterId, namespace, body.get("yaml"));
        return Result.success();
    }

    @PutMapping("/deployments/{name}")
    public Result<Void> updateDeployment(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @PathVariable String name, @RequestBody Map<String, String> body) {
        workloadService.updateDeployment(clusterId, namespace, name, body.get("yaml"));
        return Result.success();
    }

    @DeleteMapping("/deployments/{name}")
    public Result<Void> deleteDeployment(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        workloadService.deleteDeployment(clusterId, namespace, name);
        return Result.success();
    }

    @PostMapping("/deployments/{name}/scale")
    public Result<Void> scaleDeployment(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @PathVariable String name, @RequestBody Map<String, Integer> body) {
        workloadService.scaleDeployment(clusterId, namespace, name, body.get("replicas"));
        return Result.success();
    }

    @PostMapping("/deployments/{name}/restart")
    public Result<Void> restartDeployment(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        workloadService.restartDeployment(clusterId, namespace, name);
        return Result.success();
    }

    @GetMapping("/deployments/{name}/revisions")
    public Result<List<Map<String, Object>>> getDeploymentRevisions(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        return Result.success(workloadService.getDeploymentRevisions(clusterId, namespace, name));
    }

    @PostMapping("/deployments/{name}/rollback")
    public Result<Void> rollbackDeployment(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @PathVariable String name, @RequestBody Map<String, Long> body) {
        workloadService.rollbackDeployment(clusterId, namespace, name, body.get("revision"));
        return Result.success();
    }

    // ====== StatefulSets ======

    @GetMapping("/statefulsets")
    public Result<List<Map<String, Object>>> listStatefulSets(
            @PathVariable Long clusterId, @PathVariable String namespace) {
        return Result.success(workloadService.listStatefulSets(clusterId, namespace));
    }

    @GetMapping("/statefulsets/{name}")
    public Result<Map<String, Object>> getStatefulSet(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        return Result.success(workloadService.getStatefulSet(clusterId, namespace, name));
    }

    @PostMapping("/statefulsets")
    public Result<Void> createStatefulSet(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @RequestBody Map<String, String> body) {
        workloadService.createStatefulSet(clusterId, namespace, body.get("yaml"));
        return Result.success();
    }

    @PutMapping("/statefulsets/{name}")
    public Result<Void> updateStatefulSet(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @PathVariable String name, @RequestBody Map<String, String> body) {
        workloadService.updateStatefulSet(clusterId, namespace, name, body.get("yaml"));
        return Result.success();
    }

    @DeleteMapping("/statefulsets/{name}")
    public Result<Void> deleteStatefulSet(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        workloadService.deleteStatefulSet(clusterId, namespace, name);
        return Result.success();
    }

    @PostMapping("/statefulsets/{name}/scale")
    public Result<Void> scaleStatefulSet(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @PathVariable String name, @RequestBody Map<String, Integer> body) {
        workloadService.scaleStatefulSet(clusterId, namespace, name, body.get("replicas"));
        return Result.success();
    }

    @PostMapping("/statefulsets/{name}/restart")
    public Result<Void> restartStatefulSet(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        workloadService.restartStatefulSet(clusterId, namespace, name);
        return Result.success();
    }

    // ====== DaemonSets ======

    @GetMapping("/daemonsets")
    public Result<List<Map<String, Object>>> listDaemonSets(
            @PathVariable Long clusterId, @PathVariable String namespace) {
        return Result.success(workloadService.listDaemonSets(clusterId, namespace));
    }

    @GetMapping("/daemonsets/{name}")
    public Result<Map<String, Object>> getDaemonSet(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        return Result.success(workloadService.getDaemonSet(clusterId, namespace, name));
    }

    @PostMapping("/daemonsets")
    public Result<Void> createDaemonSet(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @RequestBody Map<String, String> body) {
        workloadService.createDaemonSet(clusterId, namespace, body.get("yaml"));
        return Result.success();
    }

    @PutMapping("/daemonsets/{name}")
    public Result<Void> updateDaemonSet(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @PathVariable String name, @RequestBody Map<String, String> body) {
        workloadService.updateDaemonSet(clusterId, namespace, name, body.get("yaml"));
        return Result.success();
    }

    @DeleteMapping("/daemonsets/{name}")
    public Result<Void> deleteDaemonSet(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        workloadService.deleteDaemonSet(clusterId, namespace, name);
        return Result.success();
    }

    @PostMapping("/daemonsets/{name}/restart")
    public Result<Void> restartDaemonSet(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        workloadService.restartDaemonSet(clusterId, namespace, name);
        return Result.success();
    }
}
