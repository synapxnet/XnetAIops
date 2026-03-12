package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.entity.K8sPipeline;
import com.synapxnet.aiopsk8sservice.service.K8sPipelineService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/devops/projects/{projectId}/pipelines")
public class K8sPipelineController {

    private final K8sPipelineService pipelineService;

    public K8sPipelineController(K8sPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @GetMapping
    public Result<List<K8sPipeline>> list(@PathVariable Long projectId) {
        return Result.success(pipelineService.listPipelines(projectId));
    }

    @GetMapping("/{id}")
    public Result<K8sPipeline> get(@PathVariable Long projectId, @PathVariable Long id) {
        return Result.success(pipelineService.getPipeline(id));
    }

    @PostMapping
    public Result<K8sPipeline> create(@PathVariable Long projectId, @RequestBody K8sPipeline pipeline) {
        pipeline.setProjectId(projectId);
        return Result.success(pipelineService.createPipeline(pipeline));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long projectId, @PathVariable Long id, @RequestBody K8sPipeline pipeline) {
        pipeline.setId(id);
        pipeline.setProjectId(projectId);
        pipelineService.updatePipeline(pipeline);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long projectId, @PathVariable Long id) {
        pipelineService.deletePipeline(projectId, id);
        return Result.success();
    }

    @GetMapping("/{id}/jenkinsfile")
    public Result<Map<String, String>> getJenkinsfile(@PathVariable Long projectId, @PathVariable Long id) {
        String content = pipelineService.getJenkinsfile(id);
        return Result.success(Map.of("jenkinsfile", content != null ? content : ""));
    }

    @PutMapping("/{id}/jenkinsfile")
    public Result<Void> updateJenkinsfile(@PathVariable Long projectId, @PathVariable Long id,
                                           @RequestBody Map<String, String> body) {
        pipelineService.updateJenkinsfile(id, body.get("jenkinsfile"));
        return Result.success();
    }
}
