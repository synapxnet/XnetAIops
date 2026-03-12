package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.service.K8sConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/clusters/{clusterId}/namespaces/{namespace}/configs")
public class K8sConfigController {

    private final K8sConfigService configService;

    public K8sConfigController(K8sConfigService configService) {
        this.configService = configService;
    }

    // ====== ConfigMaps ======

    @GetMapping("/configmaps")
    public Result<List<Map<String, Object>>> listConfigMaps(
            @PathVariable Long clusterId, @PathVariable String namespace) {
        return Result.success(configService.listConfigMaps(clusterId, namespace));
    }

    @GetMapping("/configmaps/{name}")
    public Result<Map<String, Object>> getConfigMap(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        return Result.success(configService.getConfigMap(clusterId, namespace, name));
    }

    @PostMapping("/configmaps")
    public Result<Void> createConfigMap(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) body.get("data");
        configService.createConfigMap(clusterId, namespace, name, data);
        return Result.success();
    }

    @PutMapping("/configmaps/{name}")
    public Result<Void> updateConfigMap(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @PathVariable String name, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) body.get("data");
        configService.updateConfigMap(clusterId, namespace, name, data);
        return Result.success();
    }

    @DeleteMapping("/configmaps/{name}")
    public Result<Void> deleteConfigMap(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        configService.deleteConfigMap(clusterId, namespace, name);
        return Result.success();
    }

    // ====== Secrets ======

    @GetMapping("/secrets")
    public Result<List<Map<String, Object>>> listSecrets(
            @PathVariable Long clusterId, @PathVariable String namespace) {
        return Result.success(configService.listSecrets(clusterId, namespace));
    }

    @GetMapping("/secrets/{name}")
    public Result<Map<String, Object>> getSecret(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        return Result.success(configService.getSecret(clusterId, namespace, name));
    }

    @PostMapping("/secrets")
    public Result<Void> createSecret(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String type = (String) body.get("type");
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) body.get("data");
        configService.createSecret(clusterId, namespace, name, type, data);
        return Result.success();
    }

    @PutMapping("/secrets/{name}")
    public Result<Void> updateSecret(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @PathVariable String name, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) body.get("data");
        configService.updateSecret(clusterId, namespace, name, data);
        return Result.success();
    }

    @DeleteMapping("/secrets/{name}")
    public Result<Void> deleteSecret(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        configService.deleteSecret(clusterId, namespace, name);
        return Result.success();
    }
}
