package com.synapxnet.aiopsregservice.controller;

import com.synapxnet.aiopsregservice.common.Result;
import com.synapxnet.aiopsregservice.entity.Registry;
import com.synapxnet.aiopsregservice.service.RegistryApiService;
import com.synapxnet.aiopsregservice.service.RegistryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reg/registries/{id}/users")
public class RegistryUserController {

    private final RegistryService registryService;
    private final RegistryApiService registryApiService;

    public RegistryUserController(RegistryService registryService, RegistryApiService registryApiService) {
        this.registryService = registryService;
        this.registryApiService = registryApiService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(@PathVariable Long id) {
        Registry registry = requireRunning(id);
        return Result.success(registryApiService.listUsers(registry));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@PathVariable Long id, @RequestBody Map<String, Object> userInfo) {
        Registry registry = requireRunning(id);
        return Result.success(registryApiService.createUser(registry, userInfo));
    }

    @PutMapping("/{userId}")
    public Result<Void> update(@PathVariable Long id, @PathVariable String userId,
                                @RequestBody Map<String, Object> userInfo) {
        Registry registry = requireRunning(id);
        registryApiService.updateUser(registry, userId, userInfo);
        return Result.success();
    }

    @DeleteMapping("/{userId}")
    public Result<Void> delete(@PathVariable Long id, @PathVariable String userId) {
        Registry registry = requireRunning(id);
        registryApiService.deleteUser(registry, userId);
        return Result.success();
    }

    private Registry requireRunning(Long id) {
        Registry registry = registryService.getById(id);
        if (!"running".equals(registry.getStatus())) {
            throw new IllegalArgumentException("仓库未运行，当前状态: " + registry.getStatus());
        }
        return registry;
    }
}
