package com.synapxnet.aiopsclmservice.controller;

import com.synapxnet.aiopsclmservice.common.Result;
import com.synapxnet.aiopsclmservice.entity.RedisDeployConfig;
import com.synapxnet.aiopsclmservice.entity.RedisInstance;
import com.synapxnet.aiopsclmservice.service.RedisInstanceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Redis 实例管理控制器
 */
@RestController
@RequestMapping("/api/clm/redis-instances")
public class RedisInstanceController {

    private final RedisInstanceService redisService;

    public RedisInstanceController(RedisInstanceService redisService) {
        this.redisService = redisService;
    }

    // ==================== CRUD ====================

    @GetMapping
    public Result<List<RedisInstance>> list(@RequestParam(value = "clusterId", required = false) Long clusterId) {
        if (clusterId != null) {
            return Result.success(redisService.listByClusterId(clusterId));
        }
        return Result.success(redisService.listAll());
    }

    @GetMapping("/{id}")
    public Result<RedisInstance> get(@PathVariable("id") Long id) {
        return Result.success(redisService.getById(id));
    }

    @PostMapping
    public Result<RedisInstance> create(@RequestBody RedisInstance instance,
                                        @RequestHeader(value = "X-User-Id", required = false) String userId) {
        if (userId != null) instance.setCreatedBy(userId);
        return Result.success(redisService.create(instance));
    }

    @PutMapping("/{id}")
    public Result<RedisInstance> update(@PathVariable("id") Long id, @RequestBody RedisInstance instance) {
        instance.setId(id);
        return Result.success(redisService.update(instance));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        redisService.delete(id);
        return Result.success();
    }

    // ==================== 部署操作 ====================

    @PostMapping("/test-connection")
    public Result<Map<String, Object>> testConnection(@RequestBody RedisInstance instance) {
        return Result.success(redisService.testConnection(instance));
    }

    @PostMapping("/{id}/deploy")
    public Result<Map<String, Object>> deploy(@PathVariable("id") Long id,
                                               @RequestBody RedisDeployConfig config) {
        return Result.success(redisService.deploy(id, config));
    }

    @PostMapping("/preview-script")
    public Result<Map<String, Object>> previewScript(@RequestBody RedisDeployConfig config) {
        String script = redisService.getDeployScript(config);
        return Result.success(Map.of("script", script));
    }

    // ==================== 状态管理 ====================

    @GetMapping("/{id}/status")
    public Result<Map<String, Object>> checkStatus(@PathVariable("id") Long id) {
        return Result.success(redisService.checkStatus(id));
    }

    @PostMapping("/{id}/start")
    public Result<Map<String, Object>> start(@PathVariable("id") Long id) {
        return Result.success(redisService.startRedis(id));
    }

    @PostMapping("/{id}/stop")
    public Result<Map<String, Object>> stop(@PathVariable("id") Long id) {
        return Result.success(redisService.stopRedis(id));
    }

    @PostMapping("/{id}/restart")
    public Result<Map<String, Object>> restart(@PathVariable("id") Long id) {
        return Result.success(redisService.restartRedis(id));
    }
}
