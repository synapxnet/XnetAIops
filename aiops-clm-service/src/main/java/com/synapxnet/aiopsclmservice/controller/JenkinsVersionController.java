package com.synapxnet.aiopsclmservice.controller;

import com.synapxnet.aiopsclmservice.common.Result;
import com.synapxnet.aiopsclmservice.entity.JenkinsVersion;
import com.synapxnet.aiopsclmservice.service.JenkinsVersionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Jenkins 版本管理控制器
 */
@RestController
@RequestMapping("/api/clm/jenkins-versions")
public class JenkinsVersionController {

    private final JenkinsVersionService versionService;

    public JenkinsVersionController(JenkinsVersionService versionService) {
        this.versionService = versionService;
    }

    @GetMapping
    public Result<List<JenkinsVersion>> listStable() {
        return Result.success(versionService.getStableVersions());
    }

    @GetMapping("/lts")
    public Result<List<JenkinsVersion>> listLts() {
        return Result.success(versionService.getLtsVersions());
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
