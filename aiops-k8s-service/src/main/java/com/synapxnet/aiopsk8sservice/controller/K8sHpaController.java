package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.service.K8sHpaService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/clusters/{clusterId}/namespaces/{namespace}/hpa")
public class K8sHpaController {

    private final K8sHpaService hpaService;

    public K8sHpaController(K8sHpaService hpaService) {
        this.hpaService = hpaService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(@PathVariable Long clusterId, @PathVariable String namespace) {
        return Result.success(hpaService.listHpas(clusterId, namespace));
    }

    @GetMapping("/{name}")
    public Result<Map<String, Object>> get(@PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        return Result.success(hpaService.getHpa(clusterId, namespace, name));
    }

    @PostMapping
    public Result<Void> create(@PathVariable Long clusterId, @PathVariable String namespace, @RequestBody Map<String, String> body) {
        hpaService.createHpaFromYaml(clusterId, namespace, body.get("yaml"));
        return Result.success();
    }

    @PutMapping("/{name}")
    public Result<Void> update(@PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name, @RequestBody Map<String, String> body) {
        hpaService.updateHpaFromYaml(clusterId, namespace, name, body.get("yaml"));
        return Result.success();
    }

    @DeleteMapping("/{name}")
    public Result<Void> delete(@PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        hpaService.deleteHpa(clusterId, namespace, name);
        return Result.success();
    }
}
