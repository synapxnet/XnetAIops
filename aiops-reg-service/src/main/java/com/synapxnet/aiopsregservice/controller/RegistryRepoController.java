package com.synapxnet.aiopsregservice.controller;

import com.synapxnet.aiopsregservice.common.Result;
import com.synapxnet.aiopsregservice.entity.Registry;
import com.synapxnet.aiopsregservice.service.RegistryApiService;
import com.synapxnet.aiopsregservice.service.RegistryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reg/registries/{id}/repositories")
public class RegistryRepoController {

    private final RegistryService registryService;
    private final RegistryApiService registryApiService;

    public RegistryRepoController(RegistryService registryService, RegistryApiService registryApiService) {
        this.registryService = registryService;
        this.registryApiService = registryApiService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(@PathVariable Long id,
                                                   @RequestParam(required = false) String projectName) {
        Registry registry = requireRunning(id);
        return Result.success(registryApiService.listRepositories(registry, projectName));
    }

    @GetMapping("/{repoName}/tags")
    public Result<List<Map<String, Object>>> listTags(@PathVariable Long id, @PathVariable String repoName) {
        Registry registry = requireRunning(id);
        return Result.success(registryApiService.listTags(registry, repoName));
    }

    @GetMapping("/{projectName}/{repoName}/artifacts")
    public Result<List<Map<String, Object>>> listArtifacts(@PathVariable Long id,
                                                            @PathVariable String projectName,
                                                            @PathVariable String repoName) {
        Registry registry = requireRunning(id);
        return Result.success(registryApiService.listArtifacts(registry, projectName, repoName));
    }

    @GetMapping("/{repoName}/artifacts/{reference}")
    public Result<Map<String, Object>> getArtifactDetail(@PathVariable Long id,
                                                          @PathVariable String repoName,
                                                          @PathVariable String reference) {
        Registry registry = requireRunning(id);
        return Result.success(registryApiService.getArtifactDetail(registry, repoName, reference));
    }

    private Registry requireRunning(Long id) {
        Registry registry = registryService.getById(id);
        if (!"running".equals(registry.getStatus())) {
            throw new IllegalArgumentException("仓库未运行，当前状态: " + registry.getStatus());
        }
        return registry;
    }
}
