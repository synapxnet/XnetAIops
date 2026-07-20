package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.service.K8sJobService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/clusters/{clusterId}/namespaces/{namespace}/jobs")
public class K8sJobController {

    private final K8sJobService jobService;

    public K8sJobController(K8sJobService jobService) {
        this.jobService = jobService;
    }

    // ====== Jobs ======

    @GetMapping
    public Result<List<Map<String, Object>>> listJobs(
            @PathVariable Long clusterId, @PathVariable String namespace) {
        return Result.success(jobService.listJobs(clusterId, namespace));
    }

    @GetMapping("/{name}")
    public Result<Map<String, Object>> getJob(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        return Result.success(jobService.getJob(clusterId, namespace, name));
    }

    @PostMapping
    public Result<Void> createJob(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @RequestBody Map<String, String> body) {
        jobService.createJob(clusterId, namespace, body.get("yaml"));
        return Result.success();
    }

    @DeleteMapping("/{name}")
    public Result<Void> deleteJob(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        jobService.deleteJob(clusterId, namespace, name);
        return Result.success();
    }

    @PostMapping("/{name}/rerun")
    public Result<Void> rerunJob(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        jobService.rerunJob(clusterId, namespace, name);
        return Result.success();
    }

    // ====== CronJobs ======

    @GetMapping("/cronjobs")
    public Result<List<Map<String, Object>>> listCronJobs(
            @PathVariable Long clusterId, @PathVariable String namespace) {
        return Result.success(jobService.listCronJobs(clusterId, namespace));
    }

    @GetMapping("/cronjobs/{name}")
    public Result<Map<String, Object>> getCronJob(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        return Result.success(jobService.getCronJob(clusterId, namespace, name));
    }

    @PostMapping("/cronjobs")
    public Result<Void> createCronJob(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @RequestBody Map<String, String> body) {
        jobService.createCronJob(clusterId, namespace, body.get("yaml"));
        return Result.success();
    }

    @PutMapping("/cronjobs/{name}")
    public Result<Void> updateCronJob(
            @PathVariable Long clusterId, @PathVariable String namespace,
            @PathVariable String name, @RequestBody Map<String, String> body) {
        jobService.updateCronJob(clusterId, namespace, name, body.get("yaml"));
        return Result.success();
    }

    @DeleteMapping("/cronjobs/{name}")
    public Result<Void> deleteCronJob(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        jobService.deleteCronJob(clusterId, namespace, name);
        return Result.success();
    }

    @PostMapping("/cronjobs/{name}/trigger")
    public Result<Void> triggerCronJob(
            @PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        jobService.triggerCronJob(clusterId, namespace, name);
        return Result.success();
    }
}
