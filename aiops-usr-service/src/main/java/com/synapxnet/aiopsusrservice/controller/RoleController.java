package com.synapxnet.aiopsusrservice.controller;

import com.synapxnet.aiopsusrservice.common.Result;
import com.synapxnet.aiopsusrservice.entity.Role;
import com.synapxnet.aiopsusrservice.entity.UserRoleCluster;
import com.synapxnet.aiopsusrservice.service.RoleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usr")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/roles")
    public Result<List<Role>> listRoles() {
        return Result.success(roleService.listAll());
    }

    @GetMapping("/roles/{id}")
    public Result<Role> getRole(@PathVariable("id") Long id) {
        return Result.success(roleService.getById(id));
    }

    @PostMapping("/roles")
    public Result<Role> createRole(@RequestBody Role role) {
        return Result.success(roleService.create(role));
    }

    @PutMapping("/roles/{id}")
    public Result<Role> updateRole(@PathVariable("id") Long id, @RequestBody Role role) {
        role.setId(id);
        return Result.success(roleService.update(role));
    }

    @DeleteMapping("/roles/{id}")
    public Result<Void> deleteRole(@PathVariable("id") Long id) {
        roleService.delete(id);
        return Result.success();
    }

    @GetMapping("/roles/{id}/users")
    public Result<List<UserRoleCluster>> getRoleUsers(@PathVariable("id") Long id) {
        return Result.success(roleService.getRoleUsers(id));
    }

    @GetMapping("/users/{userId}/roles")
    public Result<List<UserRoleCluster>> getUserRoles(@PathVariable("userId") Long userId) {
        return Result.success(roleService.getUserRoles(userId));
    }

    @PostMapping("/user-roles")
    public Result<UserRoleCluster> assignRole(@RequestBody UserRoleCluster mapping) {
        return Result.success(roleService.assignRole(mapping));
    }

    @DeleteMapping("/user-roles/{id}")
    public Result<Void> removeMapping(@PathVariable("id") Long id) {
        roleService.removeMapping(id);
        return Result.success();
    }
}
