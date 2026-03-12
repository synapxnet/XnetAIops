package com.synapxnet.aiopssvmservice.controller;

import com.synapxnet.aiopssvmservice.common.Result;
import com.synapxnet.aiopssvmservice.entity.Command;
import com.synapxnet.aiopssvmservice.service.CommandExecutionService;
import com.synapxnet.aiopssvmservice.service.CommandService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/svm/commands")
public class CommandController {

    private final CommandService commandService;
    private final CommandExecutionService commandExecutionService;

    public CommandController(CommandService commandService, CommandExecutionService commandExecutionService) {
        this.commandService = commandService;
        this.commandExecutionService = commandExecutionService;
    }

    @GetMapping
    public Result<List<Command>> list(@RequestParam(value = "clusterId", required = false) Long clusterId) {
        if (clusterId != null) {
            return Result.success(commandService.listByClusterId(clusterId));
        }
        return Result.success(commandService.listAll());
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable("id") Long id) {
        return Result.success(commandService.getDetail(id));
    }

    @PostMapping
    public Result<Command> create(@RequestBody Command command) {
        return Result.success(commandService.create(command));
    }

    @PutMapping("/{id}/status")
    public Result<Command> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, Object> params) {
        String status = (String) params.get("status");
        Integer progress = params.get("progress") != null ? ((Number) params.get("progress")).intValue() : null;
        return Result.success(commandService.updateStatus(id, status, progress));
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable("id") Long id) {
        commandExecutionService.cancelCommand(id);
        return Result.success();
    }
}
