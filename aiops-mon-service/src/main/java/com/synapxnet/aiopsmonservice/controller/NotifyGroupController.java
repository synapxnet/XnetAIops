package com.synapxnet.aiopsmonservice.controller;

import com.synapxnet.aiopsmonservice.common.Result;
import com.synapxnet.aiopsmonservice.entity.NotifyGroup;
import com.synapxnet.aiopsmonservice.service.NotifyGroupService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mon/notify-groups")
public class NotifyGroupController {

    private final NotifyGroupService notifyGroupService;

    public NotifyGroupController(NotifyGroupService notifyGroupService) {
        this.notifyGroupService = notifyGroupService;
    }

    @GetMapping
    public Result<List<NotifyGroup>> list() {
        return Result.success(notifyGroupService.listAll());
    }

    @GetMapping("/{id}")
    public Result<NotifyGroup> get(@PathVariable("id") Long id) {
        return Result.success(notifyGroupService.getById(id));
    }

    @PostMapping
    public Result<NotifyGroup> create(@RequestBody NotifyGroup notifyGroup) {
        return Result.success(notifyGroupService.create(notifyGroup));
    }

    @PutMapping("/{id}")
    public Result<NotifyGroup> update(@PathVariable("id") Long id, @RequestBody NotifyGroup notifyGroup) {
        notifyGroup.setId(id);
        return Result.success(notifyGroupService.update(notifyGroup));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        notifyGroupService.delete(id);
        return Result.success();
    }
}
