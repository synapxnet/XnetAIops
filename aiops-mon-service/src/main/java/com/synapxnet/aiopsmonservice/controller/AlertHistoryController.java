package com.synapxnet.aiopsmonservice.controller;

import com.synapxnet.aiopsmonservice.common.Result;
import com.synapxnet.aiopsmonservice.entity.AlertHistory;
import com.synapxnet.aiopsmonservice.service.AlertHistoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mon/alerts")
public class AlertHistoryController {

    private final AlertHistoryService alertHistoryService;

    public AlertHistoryController(AlertHistoryService alertHistoryService) {
        this.alertHistoryService = alertHistoryService;
    }

    @GetMapping
    public Result<List<AlertHistory>> list(
            @RequestParam(value = "clusterId", required = false) Long clusterId,
            @RequestParam(value = "status", required = false) String status) {
        return Result.success(alertHistoryService.list(clusterId, status));
    }

    @GetMapping("/{id}")
    public Result<AlertHistory> get(@PathVariable("id") Long id) {
        return Result.success(alertHistoryService.getById(id));
    }

    @PostMapping
    public Result<AlertHistory> create(@RequestBody AlertHistory alertHistory) {
        return Result.success(alertHistoryService.create(alertHistory));
    }

    @PutMapping("/{id}/acknowledge")
    public Result<AlertHistory> acknowledge(@PathVariable("id") Long id) {
        return Result.success(alertHistoryService.acknowledge(id));
    }

    @PutMapping("/{id}/resolve")
    public Result<AlertHistory> resolve(@PathVariable("id") Long id) {
        return Result.success(alertHistoryService.resolve(id));
    }

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.success(alertHistoryService.getSummary());
    }
}
