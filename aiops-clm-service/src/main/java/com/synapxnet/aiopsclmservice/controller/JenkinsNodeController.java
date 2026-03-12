package com.synapxnet.aiopsclmservice.controller;

import com.synapxnet.aiopsclmservice.common.Result;
import com.synapxnet.aiopsclmservice.entity.JenkinsNode;
import com.synapxnet.aiopsclmservice.entity.JenkinsNodeDeployConfig;
import com.synapxnet.aiopsclmservice.service.JenkinsNodeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Jenkins Node (Agent) 管理控制器
 */
@RestController
@RequestMapping("/api/clm/jenkins-nodes")
public class JenkinsNodeController {

    private final JenkinsNodeService nodeService;

    public JenkinsNodeController(JenkinsNodeService nodeService) {
        this.nodeService = nodeService;
    }

    // ==================== CRUD ====================

    @GetMapping
    public Result<List<JenkinsNode>> list() {
        return Result.success(nodeService.getAllNodes());
    }

    @GetMapping("/status/{status}")
    public Result<List<JenkinsNode>> listByStatus(@PathVariable("status") String status) {
        return Result.success(nodeService.getNodesByStatus(status));
    }

    @GetMapping("/{id}")
    public Result<JenkinsNode> get(@PathVariable("id") Long id) {
        return Result.success(nodeService.getNodeById(id));
    }

    @PostMapping
    public Result<JenkinsNode> create(@RequestBody JenkinsNode node) {
        return Result.success(nodeService.createNode(node));
    }

    @PutMapping("/{id}")
    public Result<JenkinsNode> update(@PathVariable("id") Long id, @RequestBody JenkinsNode node) {
        return Result.success(nodeService.updateNode(id, node));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        nodeService.deleteNode(id);
        return Result.success();
    }

    // ==================== SSH连接 ====================

    @PostMapping("/test-connection")
    public Result<Map<String, Object>> testConnection(@RequestBody JenkinsNode node) {
        return Result.success(nodeService.testConnection(node));
    }

    // ==================== 部署操作 ====================

    @PostMapping("/{id}/deploy")
    public Result<Map<String, Object>> deploy(@PathVariable("id") Long id,
            @RequestBody JenkinsNodeDeployConfig config) {
        return Result.success(nodeService.deployNode(id, config));
    }

    @PostMapping("/preview-script")
    public Result<Map<String, Object>> previewScript(
            @RequestParam(value = "osType", defaultValue = "linux") String osType,
            @RequestBody JenkinsNodeDeployConfig config) {
        String script = nodeService.getDeployScript(osType, config);
        return Result.success(Map.of("script", script));
    }

    // ==================== 状态管理 ====================

    @GetMapping("/{id}/status")
    public Result<Map<String, Object>> checkStatus(@PathVariable("id") Long id) {
        return Result.success(nodeService.checkNodeStatus(id));
    }

    @PostMapping("/{id}/start")
    public Result<Map<String, Object>> start(@PathVariable("id") Long id) {
        return Result.success(nodeService.startAgent(id));
    }

    @PostMapping("/{id}/stop")
    public Result<Map<String, Object>> stop(@PathVariable("id") Long id) {
        return Result.success(nodeService.stopAgent(id));
    }

    @PostMapping("/{id}/uninstall")
    public Result<Map<String, Object>> uninstall(@PathVariable("id") Long id) {
        return Result.success(nodeService.uninstallAgent(id));
    }
}
