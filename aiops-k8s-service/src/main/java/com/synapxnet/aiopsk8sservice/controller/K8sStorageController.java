package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.service.K8sStorageService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/clusters/{clusterId}/storage")
public class K8sStorageController {

    private final K8sStorageService storageService;

    public K8sStorageController(K8sStorageService storageService) {
        this.storageService = storageService;
    }

    // StorageClass
    @GetMapping("/storageclasses")
    public Result<List<Map<String, Object>>> listStorageClasses(@PathVariable Long clusterId) {
        return Result.success(storageService.listStorageClasses(clusterId));
    }

    @GetMapping("/storageclasses/{name}")
    public Result<Map<String, Object>> getStorageClass(
            @PathVariable Long clusterId, @PathVariable String name) {
        return Result.success(storageService.getStorageClass(clusterId, name));
    }

    // PVC
    @GetMapping("/namespaces/{namespace}/pvcs")
    public Result<List<Map<String, Object>>> listPVCs(
            @PathVariable Long clusterId, @PathVariable String namespace) {
        return Result.success(storageService.listPVCs(clusterId, namespace));
    }

    @GetMapping("/namespaces/{namespace}/pvcs/{name}")
    public Result<Map<String, Object>> getPVC(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        return Result.success(storageService.getPVC(clusterId, namespace, name));
    }

    @PostMapping("/namespaces/{namespace}/pvcs")
    public Result<Void> createPVC(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @RequestBody Map<String, String> body) {
        storageService.createPVC(clusterId, namespace, body.get("yaml"));
        return Result.success();
    }

    @DeleteMapping("/namespaces/{namespace}/pvcs/{name}")
    public Result<Void> deletePVC(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        storageService.deletePVC(clusterId, namespace, name);
        return Result.success();
    }

    // PV
    @GetMapping("/pvs")
    public Result<List<Map<String, Object>>> listPVs(@PathVariable Long clusterId) {
        return Result.success(storageService.listPVs(clusterId));
    }

    @GetMapping("/pvs/{name}")
    public Result<Map<String, Object>> getPV(
            @PathVariable Long clusterId, @PathVariable String name) {
        return Result.success(storageService.getPV(clusterId, name));
    }

    @DeleteMapping("/pvs/{name}")
    public Result<Void> deletePV(
            @PathVariable Long clusterId, @PathVariable String name) {
        storageService.deletePV(clusterId, name);
        return Result.success();
    }
}
