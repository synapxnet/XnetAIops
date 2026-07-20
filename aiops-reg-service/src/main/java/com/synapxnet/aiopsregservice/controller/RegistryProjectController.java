package com.synapxnet.aiopsregservice.controller;

import com.synapxnet.aiopsregservice.common.Result;
import com.synapxnet.aiopsregservice.entity.Registry;
import com.synapxnet.aiopsregservice.service.RegistryApiService;
import com.synapxnet.aiopsregservice.service.RegistryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reg/registries/{id}/projects")
public class RegistryProjectController {

    private final RegistryService registryService;
    private final RegistryApiService registryApiService;

    public RegistryProjectController(RegistryService registryService, RegistryApiService registryApiService) {
        this.registryService = registryService;
        this.registryApiService = registryApiService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(@PathVariable Long id) {
        Registry registry = requireRunning(id);
        return Result.success(registryApiService.listProjects(registry));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Registry registry = requireRunning(id);
        String projectName = (String) body.get("projectName");
        boolean isPublic = Boolean.TRUE.equals(body.get("isPublic"));
        return Result.success(registryApiService.createProject(registry, projectName, isPublic));
    }

    @DeleteMapping("/{projectId}")
    public Result<Void> delete(@PathVariable Long id, @PathVariable String projectId) {
        Registry registry = requireRunning(id);
        registryApiService.deleteProject(registry, projectId);
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
