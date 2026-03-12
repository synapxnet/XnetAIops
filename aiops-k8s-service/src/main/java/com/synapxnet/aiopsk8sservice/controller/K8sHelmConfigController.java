package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.entity.K8sHelmConfig;
import com.synapxnet.aiopsk8sservice.mapper.K8sHelmConfigMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/k8s/helm/config")
public class K8sHelmConfigController {

    private final K8sHelmConfigMapper configMapper;

    public K8sHelmConfigController(K8sHelmConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    @GetMapping
    public Result<List<K8sHelmConfig>> list() {
        return Result.success(configMapper.findAll());
    }

    @PutMapping
    public Result<Void> batchUpdate(@RequestBody List<K8sHelmConfig> configs) {
        for (K8sHelmConfig config : configs) {
            configMapper.upsert(config);
        }
        return Result.success();
    }
}
