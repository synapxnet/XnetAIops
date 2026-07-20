package com.synapxnet.aiopsregservice.controller;

import com.synapxnet.aiopsregservice.common.Result;
import com.synapxnet.aiopsregservice.entity.Registry;
import com.synapxnet.aiopsregservice.entity.SyncTask;
import com.synapxnet.aiopsregservice.service.ImageSyncService;
import com.synapxnet.aiopsregservice.service.RegistryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reg/registries/{id}/sync")
public class ImageSyncController {

    private final ImageSyncService imageSyncService;
    private final RegistryService registryService;

    public ImageSyncController(ImageSyncService imageSyncService, RegistryService registryService) {
        this.imageSyncService = imageSyncService;
        this.registryService = registryService;
    }

    /**
     * Quick sync: pull image from external source into this Harbor.
     * POST /api/reg/registries/{id}/sync
     * Body: { "sourceImage": "docker.io/nginx:1.25", "targetProject": "library", "syncMethod": "harbor_replication" }
     */
    @PostMapping
    public Result<SyncTask> sync(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireRunning(id);
        String sourceImage = (String) body.get("sourceImage");
        String targetProject = (String) body.get("targetProject");
        String syncMethod = (String) body.getOrDefault("syncMethod", "harbor_replication");

        if (sourceImage == null || sourceImage.isBlank()) {
            throw new IllegalArgumentException("sourceImage 不能为空");
        }

        SyncTask task;
        if ("skopeo".equals(syncMethod)) {
            task = imageSyncService.skopeoSync(id, sourceImage, targetProject);
        } else {
            task = imageSyncService.quickSync(id, sourceImage, targetProject);
        }
        return Result.success(task);
    }

    /**
     * List sync tasks for this registry.
     */
    @GetMapping("/tasks")
    public Result<List<SyncTask>> listTasks(@PathVariable Long id) {
        return Result.success(imageSyncService.listTasks(id));
    }

    /**
     * Get a single sync task detail.
     */
    @GetMapping("/tasks/{taskId}")
    public Result<SyncTask> getTask(@PathVariable Long id, @PathVariable Long taskId) {
        SyncTask task = imageSyncService.getTask(taskId);
        if (task == null || !task.getRegistryId().equals(id)) {
            throw new IllegalArgumentException("任务不存在");
        }
        return Result.success(task);
    }

    /**
     * Delete a sync task.
     */
    @DeleteMapping("/tasks/{taskId}")
    public Result<Void> deleteTask(@PathVariable Long id, @PathVariable Long taskId) {
        SyncTask task = imageSyncService.getTask(taskId);
        if (task == null || !task.getRegistryId().equals(id)) {
            throw new IllegalArgumentException("任务不存在");
        }
        imageSyncService.deleteTask(taskId);
        return Result.success();
    }

    /**
     * Retry a failed/cancelled sync task.
     * POST /api/reg/registries/{id}/sync/tasks/{taskId}/retry
     */
    @PostMapping("/tasks/{taskId}/retry")
    public Result<SyncTask> retryTask(@PathVariable Long id, @PathVariable Long taskId) {
        requireRunning(id);
        SyncTask task = imageSyncService.getTask(taskId);
        if (task == null || !task.getRegistryId().equals(id)) {
            throw new IllegalArgumentException("任务不存在");
        }
        return Result.success(imageSyncService.retryTask(taskId));
    }

    /**
     * Batch sync: pull multiple images at once.
     * POST /api/reg/registries/{id}/sync/batch
     * Body: { "images": ["nginx:1.25", "registry.k8s.io/coredns:v1.11.1"], "targetProject": "library", "syncMethod": "harbor_replication" }
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/batch")
    public Result<List<SyncTask>> batchSync(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireRunning(id);
        List<String> images = (List<String>) body.get("images");
        String targetProject = (String) body.getOrDefault("targetProject", "library");
        String syncMethod = (String) body.getOrDefault("syncMethod", "harbor_replication");

        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("images 列表不能为空");
        }
        return Result.success(imageSyncService.batchSync(id, images, targetProject, syncMethod));
    }

    /**
     * Extract image references from Kubernetes YAML.
     * POST /api/reg/registries/{id}/sync/extract-images
     * Body: { "yaml": "apiVersion: apps/v1\nkind: Deployment\n..." }
     */
    @PostMapping("/extract-images")
    public Result<List<String>> extractImages(@RequestBody Map<String, Object> body) {
        String yaml = (String) body.get("yaml");
        if (yaml == null || yaml.isBlank()) {
            throw new IllegalArgumentException("yaml 内容不能为空");
        }
        return Result.success(imageSyncService.extractImagesFromYaml(yaml));
    }

    /**
     * Manually refresh running task statuses (poll Harbor for completion).
     */
    @PostMapping("/refresh")
    public Result<Void> refresh(@PathVariable Long id) {
        imageSyncService.refreshRunningTasks();
        return Result.success();
    }

    private void requireRunning(Long id) {
        Registry registry = registryService.getById(id);
        if (!"running".equals(registry.getStatus())) {
            throw new IllegalArgumentException("仓库未运行，当前状态: " + registry.getStatus());
        }
    }
}
