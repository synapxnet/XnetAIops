package com.synapxnet.aiopsregservice.controller;

import com.synapxnet.aiopsregservice.common.Result;
import com.synapxnet.aiopsregservice.entity.Registry;
import com.synapxnet.aiopsregservice.service.RegistryApiService;
import com.synapxnet.aiopsregservice.service.RegistryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reg/registries/{id}/tags")
public class RegistryTagController {

    private final RegistryService registryService;
    private final RegistryApiService registryApiService;

    public RegistryTagController(RegistryService registryService, RegistryApiService registryApiService) {
        this.registryService = registryService;
        this.registryApiService = registryApiService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(@PathVariable Long id,
                                                   @RequestParam String repoName) {
        Registry registry = requireRunning(id);
        return Result.success(registryApiService.listTags(registry, repoName));
    }

    @DeleteMapping
    public Result<Void> deleteTag(@PathVariable Long id,
                                   @RequestParam String repoName,
                                   @RequestParam String tag) {
        Registry registry = requireRunning(id);
        registryApiService.deleteTag(registry, repoName, tag);
        return Result.success();
    }

    @GetMapping("/manifest")
    public Result<Map<String, Object>> getManifest(@PathVariable Long id,
                                                    @RequestParam String repoName,
                                                    @RequestParam String reference) {
        Registry registry = requireRunning(id);
        return Result.success(registryApiService.getManifest(registry, repoName, reference));
    }

    private Registry requireRunning(Long id) {
        Registry registry = registryService.getById(id);
        if (!"running".equals(registry.getStatus())) {
            throw new IllegalArgumentException("仓库未运行，当前状态: " + registry.getStatus());
        }
        return registry;
    }
}
