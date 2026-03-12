package com.synapxnet.aiopsusrservice.controller;

import com.synapxnet.aiopsusrservice.common.Result;
import com.synapxnet.aiopsusrservice.entity.User;
import com.synapxnet.aiopsusrservice.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usr/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Result<List<User>> list() {
        return Result.success(userService.listAll());
    }

    @GetMapping("/{id}")
    public Result<User> get(@PathVariable("id") Long id) {
        return Result.success(userService.getById(id));
    }

    @PostMapping
    public Result<User> create(@RequestBody User user) {
        return Result.success(userService.create(user));
    }

    @PutMapping("/{id}")
    public Result<User> update(@PathVariable("id") Long id, @RequestBody User user) {
        user.setId(id);
        return Result.success(userService.update(user));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        userService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/change-password")
    public Result<Void> changePassword(@PathVariable("id") Long id, @RequestBody Map<String, String> params) {
        userService.changePassword(id, params.get("oldPassword"), params.get("newPassword"));
        return Result.success();
    }
}
