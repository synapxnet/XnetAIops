package com.synapxnet.aiopsregservice.controller;

import com.synapxnet.aiopsregservice.common.Result;
import com.synapxnet.aiopsregservice.entity.DeployLog;
import com.synapxnet.aiopsregservice.service.RegistryDeployService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reg/registries/{id}/deploy")
public class RegistryDeployController {

    private final RegistryDeployService registryDeployService;

    public RegistryDeployController(RegistryDeployService registryDeployService) {
        this.registryDeployService = registryDeployService;
    }

    @PostMapping
    public Result<Void> deploy(@PathVariable Long id) {
        registryDeployService.deploy(id);
        return Result.success();
    }

    @PostMapping("/undeploy")
    public Result<Void> undeploy(@PathVariable Long id) {
        registryDeployService.undeploy(id);
        return Result.success();
    }

    @PostMapping("/start")
    public Result<Void> start(@PathVariable Long id) {
        registryDeployService.start(id);
        return Result.success();
    }

    @PostMapping("/stop")
    public Result<Void> stop(@PathVariable Long id) {
        registryDeployService.stop(id);
        return Result.success();
    }

    @PostMapping("/restart")
    public Result<Void> restart(@PathVariable Long id) {
        registryDeployService.restart(id);
        return Result.success();
    }

    @PostMapping("/upgrade")
    public Result<Void> upgrade(@PathVariable Long id) {
        registryDeployService.upgrade(id);
        return Result.success();
    }

    @PostMapping("/cancel")
    public Result<Void> cancelDeploy(@PathVariable Long id) {
        registryDeployService.cancelDeploy(id);
        return Result.success();
    }

    @GetMapping("/logs")
    public Result<List<DeployLog>> getDeployLogs(@PathVariable Long id) {
        return Result.success(registryDeployService.getDeployLogs(id));
    }
}
