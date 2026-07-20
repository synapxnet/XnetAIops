package com.synapxnet.aiopssvmservice.controller;

import com.synapxnet.aiopssvmservice.common.Result;
import com.synapxnet.aiopssvmservice.entity.Command;
import com.synapxnet.aiopssvmservice.entity.RoleInstance;
import com.synapxnet.aiopssvmservice.entity.ServiceInstance;
import com.synapxnet.aiopssvmservice.service.CommandExecutionService;
import com.synapxnet.aiopssvmservice.service.ServiceInstanceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/svm/services")
public class ServiceInstanceController {

    private final ServiceInstanceService serviceInstanceService;
    private final CommandExecutionService commandExecutionService;

    public ServiceInstanceController(ServiceInstanceService serviceInstanceService,
                                      CommandExecutionService commandExecutionService) {
        this.serviceInstanceService = serviceInstanceService;
        this.commandExecutionService = commandExecutionService;
    }

    @GetMapping
    public Result<List<ServiceInstance>> list(@RequestParam(value = "clusterId", required = false) Long clusterId) {
        if (clusterId != null) {
            return Result.success(serviceInstanceService.listByClusterId(clusterId));
        }
        return Result.success(serviceInstanceService.listAll());
    }

    @GetMapping("/{id}")
    public Result<ServiceInstance> get(@PathVariable("id") Long id) {
        return Result.success(serviceInstanceService.getById(id));
    }

    @GetMapping("/{id}/detail")
    public Result<Map<String, Object>> getDetail(@PathVariable("id") Long id) {
        return Result.success(serviceInstanceService.getDetail(id));
    }

    @PostMapping
    public Result<ServiceInstance> create(@RequestBody ServiceInstance serviceInstance) {
        return Result.success(serviceInstanceService.create(serviceInstance));
    }

    @PutMapping("/{id}")
    public Result<ServiceInstance> update(@PathVariable("id") Long id, @RequestBody ServiceInstance serviceInstance) {
        serviceInstance.setId(id);
        return Result.success(serviceInstanceService.update(serviceInstance));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        serviceInstanceService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}/roles")
    public Result<List<RoleInstance>> listRoles(@PathVariable("id") Long id) {
        return Result.success(serviceInstanceService.listRoles(id));
    }

    @PostMapping("/{id}/roles")
    public Result<RoleInstance> addRole(@PathVariable("id") Long id, @RequestBody RoleInstance roleInstance) {
        roleInstance.setServiceInstanceId(id);
        return Result.success(serviceInstanceService.addRole(roleInstance));
    }

    @DeleteMapping("/roles/{roleId}")
    public Result<Void> removeRole(@PathVariable("roleId") Long roleId) {
        serviceInstanceService.removeRole(roleId);
        return Result.success();
    }

    // --- Service lifecycle operations ---

    @PostMapping("/{id}/install")
    public Result<Command> install(@PathVariable("id") Long id) {
        return Result.success(commandExecutionService.executeServiceCommand(id, "install", "admin"));
    }

    @PostMapping("/{id}/start")
    public Result<Command> start(@PathVariable("id") Long id) {
        return Result.success(commandExecutionService.executeServiceCommand(id, "start", "admin"));
    }

    @PostMapping("/{id}/stop")
    public Result<Command> stop(@PathVariable("id") Long id) {
        return Result.success(commandExecutionService.executeServiceCommand(id, "stop", "admin"));
    }

    @PostMapping("/{id}/restart")
    public Result<Command> restart(@PathVariable("id") Long id) {
        return Result.success(commandExecutionService.executeServiceCommand(id, "restart", "admin"));
    }

    @PostMapping("/{id}/config")
    public Result<Command> pushConfig(@PathVariable("id") Long id) {
        return Result.success(commandExecutionService.executeServiceCommand(id, "config_update", "admin"));
    }
}
