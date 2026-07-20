package com.synapxnet.aiopsmonservice.controller;

import com.synapxnet.aiopsmonservice.common.Result;
import com.synapxnet.aiopsmonservice.entity.AlertRule;
import com.synapxnet.aiopsmonservice.service.AlertRuleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mon/rules")
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    public AlertRuleController(AlertRuleService alertRuleService) {
        this.alertRuleService = alertRuleService;
    }

    @GetMapping
    public Result<List<AlertRule>> list(@RequestParam(value = "clusterId", required = false) Long clusterId) {
        if (clusterId != null) {
            return Result.success(alertRuleService.listByClusterId(clusterId));
        }
        return Result.success(alertRuleService.listAll());
    }

    @GetMapping("/{id}")
    public Result<AlertRule> get(@PathVariable("id") Long id) {
        return Result.success(alertRuleService.getById(id));
    }

    @PostMapping
    public Result<AlertRule> create(@RequestBody AlertRule alertRule) {
        return Result.success(alertRuleService.create(alertRule));
    }

    @PutMapping("/{id}")
    public Result<AlertRule> update(@PathVariable("id") Long id, @RequestBody AlertRule alertRule) {
        alertRule.setId(id);
        return Result.success(alertRuleService.update(alertRule));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        alertRuleService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/toggle")
    public Result<AlertRule> toggle(@PathVariable("id") Long id) {
        return Result.success(alertRuleService.toggleEnabled(id));
    }
}
