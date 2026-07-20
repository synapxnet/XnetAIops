package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.entity.K8sDevopsProject;
import com.synapxnet.aiopsk8sservice.service.K8sDevopsProjectService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/devops/projects")
public class K8sDevopsProjectController {

    private final K8sDevopsProjectService projectService;

    public K8sDevopsProjectController(K8sDevopsProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public Result<List<K8sDevopsProject>> list() {
        return Result.success(projectService.listProjects());
    }

    @GetMapping("/{id}")
    public Result<K8sDevopsProject> get(@PathVariable Long id) {
        return Result.success(projectService.getProject(id));
    }

    @PostMapping
    public Result<K8sDevopsProject> create(@RequestBody K8sDevopsProject project) {
        return Result.success(projectService.createProject(project));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody K8sDevopsProject project) {
        project.setId(id);
        projectService.updateProject(project);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.deleteProject(id);
        return Result.success();
    }

    @PostMapping("/{id}/test-connection")
    public Result<Map<String, Object>> testConnection(@PathVariable Long id) {
        boolean ok = projectService.testConnection(id);
        return Result.success(Map.of("connected", ok));
    }
}
