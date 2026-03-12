package com.synapxnet.aiopsregservice.controller;

import com.synapxnet.aiopsregservice.common.Result;
import com.synapxnet.aiopsregservice.entity.Registry;
import com.synapxnet.aiopsregservice.service.RegistryApiService;
import com.synapxnet.aiopsregservice.service.RegistryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reg/registries/{id}/endpoints")
public class RegistryEndpointController {

    private final RegistryService registryService;
    private final RegistryApiService registryApiService;

    public RegistryEndpointController(RegistryService registryService, RegistryApiService registryApiService) {
        this.registryService = registryService;
        this.registryApiService = registryApiService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(@PathVariable Long id) {
        Registry registry = requireRunning(id);
        return Result.success(registryApiService.listEndpoints(registry));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Registry registry = requireRunning(id);
        return Result.success(registryApiService.createEndpoint(registry, body));
    }

    @PutMapping("/{endpointId}")
    public Result<Void> update(@PathVariable Long id, @PathVariable String endpointId,
                               @RequestBody Map<String, Object> body) {
        Registry registry = requireRunning(id);
        registryApiService.updateEndpoint(registry, endpointId, body);
        return Result.success();
    }

    @DeleteMapping("/{endpointId}")
    public Result<Void> delete(@PathVariable Long id, @PathVariable String endpointId) {
        Registry registry = requireRunning(id);
        registryApiService.deleteEndpoint(registry, endpointId);
        return Result.success();
    }

    @PostMapping("/ping")
    public Result<Map<String, Object>> ping(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Registry registry = requireRunning(id);
        return Result.success(registryApiService.pingEndpoint(registry, body));
    }

    private Registry requireRunning(Long id) {
        Registry registry = registryService.getById(id);
        if (!"running".equals(registry.getStatus())) {
            throw new IllegalArgumentException("仓库未运行，当前状态: " + registry.getStatus());
        }
        return registry;
    }
}
