package com.synapxnet.aiopsclmservice.controller;

import com.synapxnet.aiopsclmservice.common.Result;
import com.synapxnet.aiopsclmservice.entity.HadoopVersion;
import com.synapxnet.aiopsclmservice.service.HadoopVersionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Hadoop 版本管理控制器
 */
@RestController
@RequestMapping("/api/clm/hadoop-versions")
public class HadoopVersionController {

    private final HadoopVersionService versionService;

    public HadoopVersionController(HadoopVersionService versionService) {
        this.versionService = versionService;
    }

    @GetMapping
    public Result<List<HadoopVersion>> listAll() {
        return Result.success(versionService.getAllVersions());
    }

    @GetMapping("/stable")
    public Result<List<HadoopVersion>> listStable() {
        return Result.success(versionService.getStableVersions());
    }

    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh() {
        return Result.success(versionService.refreshVersions());
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(versionService.getVersionStats());
    }
}
