package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.service.K8sNodeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/clusters/{clusterId}/nodes")
public class K8sNodeController {

    private final K8sNodeService nodeService;

    public K8sNodeController(K8sNodeService nodeService) {
        this.nodeService = nodeService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> listNodes(@PathVariable Long clusterId) {
        return Result.success(nodeService.listNodes(clusterId));
    }

    @GetMapping("/{nodeName}")
    public Result<Map<String, Object>> getNode(@PathVariable Long clusterId, @PathVariable String nodeName) {
        return Result.success(nodeService.getNode(clusterId, nodeName));
    }

    @GetMapping("/{nodeName}/pods")
    public Result<List<Map<String, Object>>> getNodePods(@PathVariable Long clusterId, @PathVariable String nodeName) {
        return Result.success(nodeService.getNodePods(clusterId, nodeName));
    }

    @PostMapping("/{nodeName}/cordon")
    public Result<Void> cordonNode(@PathVariable Long clusterId, @PathVariable String nodeName) {
        nodeService.cordonNode(clusterId, nodeName);
        return Result.success();
    }

    @PostMapping("/{nodeName}/uncordon")
    public Result<Void> uncordonNode(@PathVariable Long clusterId, @PathVariable String nodeName) {
        nodeService.uncordonNode(clusterId, nodeName);
        return Result.success();
    }

    @PostMapping("/{nodeName}/drain")
    public Result<Void> drainNode(@PathVariable Long clusterId, @PathVariable String nodeName) {
        nodeService.drainNode(clusterId, nodeName);
        return Result.success();
    }

    @PutMapping("/{nodeName}/labels")
    public Result<Void> updateLabels(@PathVariable Long clusterId, @PathVariable String nodeName,
                                      @RequestBody Map<String, String> labels) {
        nodeService.updateLabels(clusterId, nodeName, labels);
        return Result.success();
    }

    @PutMapping("/{nodeName}/taints")
    public Result<Void> updateTaints(@PathVariable Long clusterId, @PathVariable String nodeName,
                                      @RequestBody List<Map<String, String>> taints) {
        nodeService.updateTaints(clusterId, nodeName, taints);
        return Result.success();
    }

    @GetMapping("/{nodeName}/metrics")
    public Result<Map<String, Object>> getNodeMetrics(@PathVariable Long clusterId, @PathVariable String nodeName) {
        return Result.success(nodeService.getNodeMetrics(clusterId, nodeName));
    }
}
