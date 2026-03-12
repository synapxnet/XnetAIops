package com.synapxnet.aiopsregservice.controller;

import com.synapxnet.aiopsregservice.common.Result;
import com.synapxnet.aiopsregservice.entity.Registry;
import com.synapxnet.aiopsregservice.entity.SyncTask;
import com.synapxnet.aiopsregservice.service.ImageSyncService;
import com.synapxnet.aiopsregservice.service.RegistryApiService;
import com.synapxnet.aiopsregservice.service.RegistryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reg/registries/{id}/replications")
public class RegistryReplicationController {

    private final RegistryService registryService;
    private final RegistryApiService registryApiService;
    private final ImageSyncService imageSyncService;

    public RegistryReplicationController(RegistryService registryService,
                                         RegistryApiService registryApiService,
                                         ImageSyncService imageSyncService) {
        this.registryService = registryService;
        this.registryApiService = registryApiService;
        this.imageSyncService = imageSyncService;
    }

    // ==================== Replication Policies ====================

    @GetMapping("/policies")
    public Result<List<Map<String, Object>>> listPolicies(@PathVariable Long id) {
        Registry registry = requireRunningHarbor(id);
        return Result.success(registryApiService.listReplicationPolicies(registry));
    }

    @PostMapping("/policies")
    public Result<Map<String, Object>> createPolicy(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Registry registry = requireRunningHarbor(id);
        return Result.success(registryApiService.createReplicationPolicy(registry, body));
    }

    @GetMapping("/policies/{policyId}")
    public Result<Map<String, Object>> getPolicy(@PathVariable Long id, @PathVariable Long policyId) {
        Registry registry = requireRunningHarbor(id);
        return Result.success(registryApiService.getReplicationPolicy(registry, policyId));
    }

    @PutMapping("/policies/{policyId}")
    public Result<Void> updatePolicy(@PathVariable Long id, @PathVariable Long policyId,
                                     @RequestBody Map<String, Object> body) {
        Registry registry = requireRunningHarbor(id);
        registryApiService.updateReplicationPolicy(registry, policyId, body);
        return Result.success();
    }

    @DeleteMapping("/policies/{policyId}")
    public Result<Void> deletePolicy(@PathVariable Long id, @PathVariable Long policyId) {
        Registry registry = requireRunningHarbor(id);
        registryApiService.deleteReplicationPolicy(registry, policyId);
        return Result.success();
    }

    // ==================== Replication Executions ====================

    @PostMapping("/executions")
    public Result<Map<String, Object>> triggerExecution(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Registry registry = requireRunningHarbor(id);
        return Result.success(registryApiService.triggerReplication(registry, body));
    }

    @GetMapping("/executions")
    public Result<List<Map<String, Object>>> listExecutions(@PathVariable Long id,
                                                             @RequestParam(required = false) Long policyId) {
        Registry registry = requireRunningHarbor(id);
        return Result.success(registryApiService.listReplicationExecutions(registry, policyId));
    }

    @GetMapping("/executions/{execId}/tasks")
    public Result<List<Map<String, Object>>> listTasks(@PathVariable Long id, @PathVariable Long execId) {
        Registry registry = requireRunningHarbor(id);
        return Result.success(registryApiService.getReplicationTasks(registry, execId));
    }

    // ==================== Quick Sync ====================

    @PostMapping("/quick-sync")
    public Result<SyncTask> quickSync(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String image = (String) body.get("image");
        String targetProject = (String) body.get("targetProject");
        if (image == null || image.isBlank()) {
            throw new IllegalArgumentException("image 不能为空");
        }
        return Result.success(imageSyncService.quickSync(id, image, targetProject));
    }

    // ==================== Helpers ====================

    private Registry requireRunningHarbor(Long id) {
        Registry registry = registryService.getById(id);
        if (!"running".equals(registry.getStatus())) {
            throw new IllegalArgumentException("仓库未运行，当前状态: " + registry.getStatus());
        }
        if (!"harbor".equals(registry.getRegistryType())) {
            throw new IllegalArgumentException("复制管理仅支持 Harbor 类型仓库");
        }
        return registry;
    }
}
