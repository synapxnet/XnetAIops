package com.synapxnet.aiopsregservice.service;

import com.synapxnet.aiopsregservice.entity.SyncTask;

import java.util.List;

public interface ImageSyncService {

    /**
     * Quick sync: pull an image from external source into target Harbor via Harbor Replication API.
     * Automatically creates/reuses Docker Hub endpoint and a one-shot replication policy.
     */
    SyncTask quickSync(Long registryId, String sourceImage, String targetProject);

    /**
     * Sync via skopeo: SSH into target host and execute skopeo copy.
     * For non-Harbor registries or when Harbor Replication is not available.
     */
    SyncTask skopeoSync(Long registryId, String sourceImage, String targetProject);

    /** List all sync tasks for a registry */
    List<SyncTask> listTasks(Long registryId);

    /** Get a single sync task */
    SyncTask getTask(Long taskId);

    /** Cancel or delete a sync task */
    void deleteTask(Long taskId);

    /** Retry a failed/cancelled sync task */
    SyncTask retryTask(Long taskId);

    /** Poll and update status of running Harbor replication tasks */
    void refreshRunningTasks();

    /**
     * Batch sync: pull multiple images into target Harbor.
     * @param images list of image references (e.g. ["nginx:1.25", "registry.k8s.io/coredns:v1.11.1"])
     * @return list of created sync tasks
     */
    List<SyncTask> batchSync(Long registryId, List<String> images, String targetProject, String syncMethod);

    /**
     * Extract image references from Kubernetes YAML (Deployment, DaemonSet, StatefulSet, etc.)
     * Parses all "image:" fields from the YAML content.
     */
    List<String> extractImagesFromYaml(String yamlContent);
}
