package com.synapxnet.aiopsclmservice.controller;

import com.synapxnet.aiopsclmservice.common.Result;
import com.synapxnet.aiopsclmservice.entity.Cluster;
import com.synapxnet.aiopsclmservice.entity.ClusterVariable;
import com.synapxnet.aiopsclmservice.service.ClusterService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clm/clusters")
public class ClusterController {

    private final ClusterService clusterService;

    public ClusterController(ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    @GetMapping
    public Result<List<Cluster>> list() {
        return Result.success(clusterService.listAll());
    }

    @GetMapping("/{id}")
    public Result<Cluster> get(@PathVariable("id") Long id) {
        return Result.success(clusterService.getById(id));
    }

    @PostMapping
    public Result<Cluster> create(@RequestBody Cluster cluster) {
        return Result.success(clusterService.create(cluster));
    }

    @PutMapping("/{id}")
    public Result<Cluster> update(@PathVariable("id") Long id, @RequestBody Cluster cluster) {
        cluster.setId(id);
        return Result.success(clusterService.update(cluster));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        clusterService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}/overview")
    public Result<Map<String, Object>> overview(@PathVariable("id") Long id) {
        return Result.success(clusterService.getOverview(id));
    }

    @GetMapping("/{id}/variables")
    public Result<List<ClusterVariable>> getVariables(@PathVariable("id") Long id) {
        return Result.success(clusterService.getVariables(id));
    }

    @PostMapping("/{id}/variables")
    public Result<Void> saveVariable(@PathVariable("id") Long id, @RequestBody ClusterVariable variable) {
        variable.setClusterId(id);
        clusterService.saveVariable(variable);
        return Result.success();
    }

    @DeleteMapping("/variables/{variableId}")
    public Result<Void> deleteVariable(@PathVariable("variableId") Long variableId) {
        clusterService.deleteVariable(variableId);
        return Result.success();
    }
}
