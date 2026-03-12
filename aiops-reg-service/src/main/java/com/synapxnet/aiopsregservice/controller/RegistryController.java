package com.synapxnet.aiopsregservice.controller;

import com.synapxnet.aiopsregservice.common.Result;
import com.synapxnet.aiopsregservice.entity.Registry;
import com.synapxnet.aiopsregservice.service.RegistryService;
import com.synapxnet.aiopsregservice.service.impl.SshDeployService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reg/registries")
public class RegistryController {

    private final RegistryService registryService;
    private final SshDeployService sshDeployService;

    public RegistryController(RegistryService registryService, SshDeployService sshDeployService) {
        this.registryService = registryService;
        this.sshDeployService = sshDeployService;
    }

    @GetMapping
    public Result<List<Registry>> list() {
        return Result.success(registryService.listAll());
    }

    @GetMapping("/{id}")
    public Result<Registry> getById(@PathVariable Long id) {
        return Result.success(registryService.getById(id));
    }

    @PostMapping
    public Result<Registry> create(@RequestBody Registry registry) {
        return Result.success(registryService.create(registry));
    }

    @PutMapping("/{id}")
    public Result<Registry> update(@PathVariable Long id, @RequestBody Registry registry) {
        registry.setId(id);
        return Result.success(registryService.update(registry));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        registryService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}/status")
    public Result<String> getStatus(@PathVariable Long id) {
        return Result.success(registryService.getStatus(id));
    }

    /**
     * Check if a port is occupied on a remote host via SSH.
     * GET /api/reg/registries/check-port?host=x&sshPort=22&sshUser=root&password=x&port=80
     */
    @GetMapping("/check-port")
    public Result<Map<String, Object>> checkPort(
            @RequestParam String host,
            @RequestParam(defaultValue = "22") Integer sshPort,
            @RequestParam(defaultValue = "root") String sshUser,
            @RequestParam String password,
            @RequestParam Integer port) {
        Map<String, Object> result = sshDeployService.checkPort(host, sshPort, sshUser, password, port);
        return Result.success(result);
    }
}
