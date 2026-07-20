package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.entity.K8sPipelineRun;
import com.synapxnet.aiopsk8sservice.service.K8sPipelineRunService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/devops/projects/{projectId}/pipelines/{pipelineId}/runs")
public class K8sPipelineRunController {

    private final K8sPipelineRunService runService;

    public K8sPipelineRunController(K8sPipelineRunService runService) {
        this.runService = runService;
    }

    @PostMapping
    public Result<K8sPipelineRun> trigger(@PathVariable Long projectId, @PathVariable Long pipelineId,
                                           @RequestBody(required = false) Map<String, String> params) {
        return Result.success(runService.triggerRun(projectId, pipelineId, "admin", params));
    }

    @GetMapping
    public Result<List<K8sPipelineRun>> list(@PathVariable Long projectId, @PathVariable Long pipelineId) {
        return Result.success(runService.listRuns(pipelineId));
    }

    @GetMapping("/{runId}")
    public Result<K8sPipelineRun> get(@PathVariable Long projectId, @PathVariable Long pipelineId,
                                       @PathVariable Long runId) {
        // Sync status from Jenkins before returning
        runService.syncRunStatus(projectId, pipelineId, runId);
        return Result.success(runService.getRun(runId));
    }

    @GetMapping("/{runId}/stages")
    public Result<List<Map<String, Object>>> getStages(@PathVariable Long projectId, @PathVariable Long pipelineId,
                                                        @PathVariable Long runId) {
        return Result.success(runService.getRunStages(projectId, pipelineId, runId));
    }

    @GetMapping("/{runId}/log")
    public Result<Map<String, String>> getLog(@PathVariable Long projectId, @PathVariable Long pipelineId,
                                               @PathVariable Long runId) {
        String logText = runService.getRunLog(projectId, pipelineId, runId);
        return Result.success(Map.of("log", logText != null ? logText : ""));
    }

    @GetMapping("/{runId}/stages/{nodeId}/log")
    public Result<Map<String, String>> getStageLog(@PathVariable Long projectId, @PathVariable Long pipelineId,
                                                    @PathVariable Long runId, @PathVariable String nodeId) {
        String logText = runService.getStageLog(projectId, pipelineId, runId, nodeId);
        return Result.success(Map.of("log", logText != null ? logText : ""));
    }

    @PostMapping("/{runId}/stop")
    public Result<Void> stop(@PathVariable Long projectId, @PathVariable Long pipelineId,
                              @PathVariable Long runId) {
        runService.stopRun(projectId, pipelineId, runId);
        return Result.success();
    }
}
