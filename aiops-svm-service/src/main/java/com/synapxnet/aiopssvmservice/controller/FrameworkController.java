package com.synapxnet.aiopssvmservice.controller;

import com.synapxnet.aiopssvmservice.common.Result;
import com.synapxnet.aiopssvmservice.entity.Framework;
import com.synapxnet.aiopssvmservice.entity.RoleDef;
import com.synapxnet.aiopssvmservice.entity.ServiceDef;
import com.synapxnet.aiopssvmservice.service.FrameworkService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/svm/frameworks")
public class FrameworkController {

    private final FrameworkService frameworkService;

    public FrameworkController(FrameworkService frameworkService) {
        this.frameworkService = frameworkService;
    }

    @GetMapping
    public Result<List<Framework>> list() {
        return Result.success(frameworkService.listAll());
    }

    @GetMapping("/{id}")
    public Result<Framework> get(@PathVariable("id") Long id) {
        return Result.success(frameworkService.getById(id));
    }

    @PostMapping
    public Result<Framework> create(@RequestBody Framework framework) {
        return Result.success(frameworkService.create(framework));
    }

    @PutMapping("/{id}")
    public Result<Framework> update(@PathVariable("id") Long id, @RequestBody Framework framework) {
        framework.setId(id);
        return Result.success(frameworkService.update(framework));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        frameworkService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}/services")
    public Result<List<ServiceDef>> listServiceDefs(@PathVariable("id") Long id) {
        return Result.success(frameworkService.listServiceDefs(id));
    }

    @PostMapping("/{id}/services")
    public Result<ServiceDef> createServiceDef(@PathVariable("id") Long id, @RequestBody ServiceDef serviceDef) {
        serviceDef.setFrameworkId(id);
        return Result.success(frameworkService.createServiceDef(serviceDef));
    }

    @PutMapping("/services/{serviceId}")
    public Result<ServiceDef> updateServiceDef(@PathVariable("serviceId") Long serviceId, @RequestBody ServiceDef serviceDef) {
        serviceDef.setId(serviceId);
        return Result.success(frameworkService.updateServiceDef(serviceDef));
    }

    @DeleteMapping("/services/{serviceId}")
    public Result<Void> deleteServiceDef(@PathVariable("serviceId") Long serviceId) {
        frameworkService.deleteServiceDef(serviceId);
        return Result.success();
    }

    @GetMapping("/{id}/services/{serviceId}/roles")
    public Result<List<RoleDef>> listRoleDefs(@PathVariable("id") Long id, @PathVariable("serviceId") Long serviceId) {
        return Result.success(frameworkService.listRoleDefs(serviceId));
    }

    @PostMapping("/{id}/services/{serviceId}/roles")
    public Result<RoleDef> createRoleDef(@PathVariable("id") Long id, @PathVariable("serviceId") Long serviceId, @RequestBody RoleDef roleDef) {
        roleDef.setServiceDefId(serviceId);
        return Result.success(frameworkService.createRoleDef(roleDef));
    }

    @PutMapping("/roles/{roleId}")
    public Result<RoleDef> updateRoleDef(@PathVariable("roleId") Long roleId, @RequestBody RoleDef roleDef) {
        roleDef.setId(roleId);
        return Result.success(frameworkService.updateRoleDef(roleDef));
    }

    @DeleteMapping("/roles/{roleId}")
    public Result<Void> deleteRoleDef(@PathVariable("roleId") Long roleId) {
        frameworkService.deleteRoleDef(roleId);
        return Result.success();
    }
}
