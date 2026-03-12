package com.synapxnet.aiopsclmservice.controller;

import com.synapxnet.aiopsclmservice.common.Result;
import com.synapxnet.aiopsclmservice.entity.HadoopCluster;
import com.synapxnet.aiopsclmservice.entity.HadoopDeployConfig;
import com.synapxnet.aiopsclmservice.service.HadoopClusterService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Hadoop 集群管理控制器
 */
@RestController
@RequestMapping("/api/clm/hadoop-clusters")
public class HadoopClusterController {

    private final HadoopClusterService hadoopService;

    public HadoopClusterController(HadoopClusterService hadoopService) {
        this.hadoopService = hadoopService;
    }

    // ==================== CRUD ====================

    @GetMapping
    public Result<List<HadoopCluster>> list() {
        return Result.success(hadoopService.getAll());
    }

    @GetMapping("/masters")
    public Result<List<HadoopCluster>> listMasters() {
        return Result.success(hadoopService.getMasters());
    }

    @GetMapping("/nodes")
    public Result<List<HadoopCluster>> listNodes() {
        return Result.success(hadoopService.getNodes());
    }

    @GetMapping("/{id}")
    public Result<HadoopCluster> get(@PathVariable("id") Long id) {
        return hadoopService.getById(id)
                .map(Result::success)
                .orElse(Result.error(404, "Hadoop集群节点不存在"));
    }

    @PostMapping
    public Result<HadoopCluster> create(@RequestBody HadoopCluster cluster,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return Result.success(hadoopService.create(cluster, userId));
    }

    @PutMapping("/{id}")
    public Result<HadoopCluster> update(@PathVariable("id") Long id,
            @RequestBody HadoopCluster cluster,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return Result.success(hadoopService.update(id, cluster, userId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        hadoopService.delete(id);
        return Result.success();
    }

    // ==================== 部署操作 ====================

    @PostMapping("/test-connection")
    public Result<Map<String, Object>> testConnection(@RequestBody HadoopCluster cluster) {
        return Result.success(hadoopService.testConnection(cluster));
    }

    @PostMapping("/{id}/deploy")
    public Result<Map<String, Object>> deploy(@PathVariable("id") Long id,
            @RequestBody HadoopDeployConfig config) {
        return Result.success(hadoopService.deploy(id, config));
    }

    @PostMapping("/preview-script")
    public Result<Map<String, Object>> previewScript(
            @RequestParam(value = "osType", defaultValue = "centos7") String osType,
            @RequestParam(value = "nodeType", defaultValue = "master") String nodeType,
            @RequestBody HadoopDeployConfig config) {
        String script = hadoopService.previewScript(osType, nodeType, config);
        return Result.success(Map.of("script", script));
    }

    // ==================== 状态管理 ====================

    @GetMapping("/{id}/status")
    public Result<Map<String, Object>> checkStatus(@PathVariable("id") Long id) {
        return Result.success(hadoopService.checkStatus(id));
    }

    @GetMapping("/status/{status}")
    public Result<List<HadoopCluster>> listByStatus(@PathVariable("status") String status) {
        // Filter from getAll by status
        List<HadoopCluster> result = hadoopService.getAll().stream()
                .filter(c -> status.equals(c.getStatus()))
                .toList();
        return Result.success(result);
    }

    @PostMapping("/{id}/start")
    public Result<Map<String, Object>> start(@PathVariable("id") Long id) {
        return Result.success(hadoopService.startServices(id));
    }

    @PostMapping("/{id}/stop")
    public Result<Map<String, Object>> stop(@PathVariable("id") Long id) {
        return Result.success(hadoopService.stopServices(id));
    }

    @PostMapping("/{id}/restart")
    public Result<Map<String, Object>> restart(@PathVariable("id") Long id) {
        return Result.success(hadoopService.restartServices(id));
    }

    // ==================== 集群健康 ====================

    @GetMapping("/{id}/health")
    public Result<Map<String, Object>> getClusterHealth(@PathVariable("id") Long id) {
        return Result.success(hadoopService.getClusterHealth(id));
    }

    @GetMapping("/{id}/hdfs-status")
    public Result<Map<String, Object>> getHdfsStatus(@PathVariable("id") Long id) {
        return Result.success(hadoopService.getHdfsStatus(id));
    }

    @GetMapping("/{id}/yarn-status")
    public Result<Map<String, Object>> getYarnStatus(@PathVariable("id") Long id) {
        return Result.success(hadoopService.getYarnStatus(id));
    }
}
