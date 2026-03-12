package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.entity.K8sAlertHistory;
import com.synapxnet.aiopsk8sservice.entity.K8sAlertRule;
import com.synapxnet.aiopsk8sservice.service.K8sAlertService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/clusters/{clusterId}/alerts")
public class K8sAlertController {

    private final K8sAlertService alertService;

    public K8sAlertController(K8sAlertService alertService) {
        this.alertService = alertService;
    }

    // ====== Alert Rules ======
    @GetMapping("/rules")
    public Result<List<K8sAlertRule>> listRules(@PathVariable Long clusterId) {
        return Result.success(alertService.listRules(clusterId));
    }

    @GetMapping("/rules/{id}")
    public Result<K8sAlertRule> getRule(@PathVariable Long id) {
        return Result.success(alertService.getRule(id));
    }

    @PostMapping("/rules")
    public Result<Void> createRule(@PathVariable Long clusterId, @RequestBody K8sAlertRule rule) {
        rule.setClusterId(clusterId);
        alertService.createRule(rule);
        return Result.success();
    }

    @PutMapping("/rules/{id}")
    public Result<Void> updateRule(@PathVariable Long id, @RequestBody K8sAlertRule rule) {
        rule.setId(id);
        alertService.updateRule(rule);
        return Result.success();
    }

    @DeleteMapping("/rules/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        alertService.deleteRule(id);
        return Result.success();
    }

    @PostMapping("/rules/{id}/toggle")
    public Result<Void> toggleRule(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        alertService.toggleRule(id, body.getOrDefault("enabled", false));
        return Result.success();
    }

    // ====== Alert History ======
    @GetMapping("/history")
    public Result<List<K8sAlertHistory>> listHistory(
            @PathVariable Long clusterId,
            @RequestParam(defaultValue = "100") int limit) {
        return Result.success(alertService.listHistory(clusterId, limit));
    }

    @GetMapping("/rules/{ruleId}/history")
    public Result<List<K8sAlertHistory>> listHistoryByRule(
            @PathVariable Long ruleId,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(alertService.listHistoryByRule(ruleId, limit));
    }
}
