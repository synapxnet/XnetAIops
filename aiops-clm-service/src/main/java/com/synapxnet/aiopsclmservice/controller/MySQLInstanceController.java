package com.synapxnet.aiopsclmservice.controller;

import com.synapxnet.aiopsclmservice.common.Result;
import com.synapxnet.aiopsclmservice.entity.MySQLDeployConfig;
import com.synapxnet.aiopsclmservice.entity.MySQLInstance;
import com.synapxnet.aiopsclmservice.service.MySQLInstanceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MySQL 实例管理控制器
 */
@RestController
@RequestMapping("/api/clm/mysql-instances")
public class MySQLInstanceController {

    private final MySQLInstanceService mysqlService;

    public MySQLInstanceController(MySQLInstanceService mysqlService) {
        this.mysqlService = mysqlService;
    }

    // ==================== CRUD ====================

    @GetMapping
    public Result<List<MySQLInstance>> list(@RequestParam(value = "clusterId", required = false) Long clusterId) {
        if (clusterId != null) {
            return Result.success(mysqlService.listByClusterId(clusterId));
        }
        return Result.success(mysqlService.listAll());
    }

    @GetMapping("/{id}")
    public Result<MySQLInstance> get(@PathVariable("id") Long id) {
        return Result.success(mysqlService.getById(id));
    }

    @PostMapping
    public Result<MySQLInstance> create(@RequestBody MySQLInstance instance,
                                        @RequestHeader(value = "X-User-Id", required = false) String userId) {
        if (userId != null) instance.setCreatedBy(userId);
        return Result.success(mysqlService.create(instance));
    }

    @PutMapping("/{id}")
    public Result<MySQLInstance> update(@PathVariable("id") Long id, @RequestBody MySQLInstance instance) {
        instance.setId(id);
        return Result.success(mysqlService.update(instance));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        mysqlService.delete(id);
        return Result.success();
    }

    // ==================== 部署操作 ====================

    @PostMapping("/test-connection")
    public Result<Map<String, Object>> testConnection(@RequestBody MySQLInstance instance) {
        return Result.success(mysqlService.testConnection(instance));
    }

    @PostMapping("/{id}/deploy")
    public Result<Map<String, Object>> deploy(@PathVariable("id") Long id,
                                               @RequestBody MySQLDeployConfig config) {
        return Result.success(mysqlService.deploy(id, config));
    }

    @PostMapping("/preview-script")
    public Result<Map<String, Object>> previewScript(@RequestBody MySQLDeployConfig config) {
        String script = mysqlService.getDeployScript(config);
        return Result.success(Map.of("script", script));
    }

    // ==================== 状态管理 ====================

    @GetMapping("/{id}/status")
    public Result<Map<String, Object>> checkStatus(@PathVariable("id") Long id) {
        return Result.success(mysqlService.checkStatus(id));
    }

    @PostMapping("/{id}/start")
    public Result<Map<String, Object>> start(@PathVariable("id") Long id) {
        return Result.success(mysqlService.startMySQL(id));
    }

    @PostMapping("/{id}/stop")
    public Result<Map<String, Object>> stop(@PathVariable("id") Long id) {
        return Result.success(mysqlService.stopMySQL(id));
    }

    @PostMapping("/{id}/restart")
    public Result<Map<String, Object>> restart(@PathVariable("id") Long id) {
        return Result.success(mysqlService.restartMySQL(id));
    }
}
