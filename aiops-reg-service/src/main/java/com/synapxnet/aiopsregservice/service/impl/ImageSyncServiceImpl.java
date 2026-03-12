package com.synapxnet.aiopsregservice.service.impl;

import com.jcraft.jsch.Session;
import com.synapxnet.aiopsregservice.entity.Registry;
import com.synapxnet.aiopsregservice.entity.SyncTask;
import com.synapxnet.aiopsregservice.mapper.SyncTaskMapper;
import com.synapxnet.aiopsregservice.service.ImageSyncService;
import com.synapxnet.aiopsregservice.service.RegistryApiService;
import com.synapxnet.aiopsregservice.service.RegistryService;
import com.synapxnet.aiopsregservice.util.SshExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ImageSyncServiceImpl implements ImageSyncService {

    private static final Logger log = LoggerFactory.getLogger(ImageSyncServiceImpl.class);

    private final SyncTaskMapper syncTaskMapper;
    private final RegistryService registryService;
    private final RegistryApiService registryApiService;

    /** Cache Docker Hub endpoint ID per registry to avoid creating duplicates */
    private final Map<Long, Long> dockerHubEndpointCache = new ConcurrentHashMap<>();

    public ImageSyncServiceImpl(SyncTaskMapper syncTaskMapper,
                                RegistryService registryService,
                                RegistryApiService registryApiService) {
        this.syncTaskMapper = syncTaskMapper;
        this.registryService = registryService;
        this.registryApiService = registryApiService;
    }

    @Override
    public SyncTask quickSync(Long registryId, String sourceImage, String targetProject) {
        Registry registry = registryService.getById(registryId);
        if (!"harbor".equals(registry.getRegistryType())) {
            throw new IllegalArgumentException("快速同步仅支持 Harbor 类型仓库，请使用 Skopeo 方式");
        }
        if (!"running".equals(registry.getStatus())) {
            throw new IllegalArgumentException("仓库未运行");
        }

        // Parse source image: registry/repo:tag
        ImageRef ref = parseImageRef(sourceImage);

        // 1. Ensure external registry endpoint exists in Harbor
        long endpointId = ensureEndpoint(registry, ref);

        // 2. Create one-shot replication policy
        Map<String, Object> policy = createOneShotPolicy(registry, ref, endpointId, targetProject);
        Long policyId = toLong(policy.get("id"));

        // If the policy was created but Harbor returned 201 with no body, use location header ID
        if (policyId == null) {
            String policyName = buildPolicyName(ref);
            List<Map<String, Object>> policies = registryApiService.listReplicationPoliciesByName(registry, policyName);
            for (Map<String, Object> p : policies) {
                if (policyName.equals(p.get("name"))) {
                    policyId = toLong(p.get("id"));
                    break;
                }
            }
        }

        if (policyId == null) {
            throw new RuntimeException("无法获取 Replication Policy ID，请检查 Harbor 状态");
        }

        // 3. Trigger replication execution
        Map<String, Object> execBody = new HashMap<>();
        execBody.put("policy_id", policyId);
        Map<String, Object> execution = registryApiService.triggerReplication(registry, execBody);
        Long executionId = toLong(execution.get("id"));

        // Fallback: if execution ID not returned, find the latest execution for this policy
        if (executionId == null && policyId != null) {
            List<Map<String, Object>> executions = registryApiService.listReplicationExecutions(registry, policyId);
            if (!executions.isEmpty()) {
                // Harbor returns executions sorted by start_time desc, take the first (latest)
                executionId = toLong(executions.get(0).get("id"));
            }
        }

        // 4. Create sync task record
        SyncTask task = new SyncTask();
        task.setRegistryId(registryId);
        task.setSourceImage(sourceImage);
        task.setTargetProject(targetProject);
        task.setSyncMethod("harbor_replication");
        task.setHarborPolicyId(policyId);
        task.setHarborExecutionId(executionId);
        task.setStatus("running");
        task.setStatusDetail("Harbor replication triggered");
        syncTaskMapper.insert(task);

        return task;
    }

    @Override
    @Async
    public SyncTask skopeoSync(Long registryId, String sourceImage, String targetProject) {
        Registry registry = registryService.getById(registryId);
        if (!"running".equals(registry.getStatus())) {
            throw new IllegalArgumentException("仓库未运行");
        }
        if (!"ssh".equals(registry.getDeployMode())) {
            throw new IllegalArgumentException("Skopeo 同步仅支持 SSH 部署模式的仓库");
        }

        SyncTask task = new SyncTask();
        task.setRegistryId(registryId);
        task.setSourceImage(sourceImage);
        task.setTargetProject(targetProject);
        task.setSyncMethod("skopeo");
        task.setStatus("running");
        task.setStatusDetail("Skopeo copy starting...");
        syncTaskMapper.insert(task);

        // Build target image reference
        String targetImage = buildTargetImage(registry, sourceImage, targetProject);
        String command = String.format(
                "skopeo copy --dest-tls-verify=false docker://%s docker://%s",
                sourceImage, targetImage
        );

        Session session = null;
        try {
            session = SshExecutor.connect(
                    registry.getHost(),
                    registry.getSshPort() != null ? registry.getSshPort() : 22,
                    registry.getSshUser(),
                    registry.getEncryptedPassword(),
                    registry.getEncryptedPrivateKey()
            );

            StringBuilder output = new StringBuilder();
            int exitCode = SshExecutor.executeWithCallback(session, command, 600000,
                    line -> output.append(line).append("\n"),
                    line -> output.append("[ERR] ").append(line).append("\n")
            );

            if (exitCode == 0) {
                task.setStatus("success");
                task.setStatusDetail(output.toString());
            } else {
                task.setStatus("failed");
                task.setStatusDetail("Exit code: " + exitCode + "\n" + output);
            }
        } catch (Exception e) {
            log.error("Skopeo sync failed for task {}: {}", task.getId(), e.getMessage(), e);
            task.setStatus("failed");
            task.setStatusDetail("Skopeo error: " + e.getMessage());
        } finally {
            SshExecutor.disconnect(session);
            syncTaskMapper.update(task);
        }

        return task;
    }

    @Override
    public List<SyncTask> listTasks(Long registryId) {
        return syncTaskMapper.findByRegistryId(registryId);
    }

    @Override
    public SyncTask getTask(Long taskId) {
        return syncTaskMapper.findById(taskId);
    }

    @Override
    public void deleteTask(Long taskId) {
        syncTaskMapper.deleteById(taskId);
    }

    @Override
    public SyncTask retryTask(Long taskId) {
        SyncTask old = syncTaskMapper.findById(taskId);
        if (old == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if ("running".equals(old.getStatus())) {
            throw new IllegalArgumentException("任务正在运行中，无需重试");
        }

        if ("skopeo".equals(old.getSyncMethod())) {
            // Skopeo: run async, update the same record
            old.setStatus("running");
            old.setStatusDetail("Skopeo copy retrying...");
            syncTaskMapper.update(old);
            retrySkopeo(old);
            return old;
        }

        // Harbor replication: re-trigger on the same task record
        try {
            Registry registry = registryService.getById(old.getRegistryId());
            ImageRef ref = parseImageRef(old.getSourceImage());
            long endpointId = ensureEndpoint(registry, ref);
            Map<String, Object> policy = createOneShotPolicy(registry, ref, endpointId, old.getTargetProject());
            Long policyId = toLong(policy.get("id"));
            if (policyId == null) {
                String policyName = buildPolicyName(ref);
                List<Map<String, Object>> policies = registryApiService.listReplicationPoliciesByName(registry, policyName);
                for (Map<String, Object> p : policies) {
                    if (policyName.equals(p.get("name"))) {
                        policyId = toLong(p.get("id"));
                        break;
                    }
                }
            }
            if (policyId == null) {
                throw new RuntimeException("无法获取 Replication Policy ID");
            }

            Map<String, Object> execBody = new HashMap<>();
            execBody.put("policy_id", policyId);
            Map<String, Object> execution = registryApiService.triggerReplication(registry, execBody);
            Long executionId = toLong(execution.get("id"));
            if (executionId == null) {
                List<Map<String, Object>> executions = registryApiService.listReplicationExecutions(registry, policyId);
                if (!executions.isEmpty()) {
                    executionId = toLong(executions.get(0).get("id"));
                }
            }

            old.setHarborPolicyId(policyId);
            old.setHarborExecutionId(executionId);
            old.setStatus("running");
            old.setStatusDetail("Harbor replication retried");
            syncTaskMapper.update(old);
            return old;
        } catch (Exception e) {
            old.setStatus("failed");
            old.setStatusDetail("重试失败: " + e.getMessage());
            syncTaskMapper.update(old);
            throw e;
        }
    }

    @Async
    protected void retrySkopeo(SyncTask task) {
        Registry registry = registryService.getById(task.getRegistryId());
        String targetImage = buildTargetImage(registry, task.getSourceImage(), task.getTargetProject());
        String command = String.format(
                "skopeo copy --dest-tls-verify=false docker://%s docker://%s",
                task.getSourceImage(), targetImage
        );
        com.jcraft.jsch.Session session = null;
        try {
            session = SshExecutor.connect(
                    registry.getHost(),
                    registry.getSshPort() != null ? registry.getSshPort() : 22,
                    registry.getSshUser(),
                    registry.getEncryptedPassword(),
                    registry.getEncryptedPrivateKey()
            );
            StringBuilder output = new StringBuilder();
            int exitCode = SshExecutor.executeWithCallback(session, command, 600000,
                    line -> output.append(line).append("\n"),
                    line -> output.append("[ERR] ").append(line).append("\n")
            );
            task.setStatus(exitCode == 0 ? "success" : "failed");
            task.setStatusDetail(exitCode == 0 ? output.toString() : "Exit code: " + exitCode + "\n" + output);
        } catch (Exception e) {
            log.error("Skopeo retry failed for task {}: {}", task.getId(), e.getMessage(), e);
            task.setStatus("failed");
            task.setStatusDetail("Skopeo error: " + e.getMessage());
        } finally {
            SshExecutor.disconnect(session);
            syncTaskMapper.update(task);
        }
    }

    @Scheduled(fixedDelay = 15000, initialDelay = 10000)
    public void pollRunningTasks() {
        try {
            refreshRunningTasks();
        } catch (Exception e) {
            log.warn("Scheduled sync task polling failed: {}", e.getMessage());
        }
    }

    @Override
    public void refreshRunningTasks() {
        List<SyncTask> runningTasks = syncTaskMapper.findRunningHarborTasks();
        for (SyncTask task : runningTasks) {
            try {
                Registry registry = registryService.getById(task.getRegistryId());
                if (task.getHarborExecutionId() == null) {
                    continue;
                }
                List<Map<String, Object>> executions = registryApiService.listReplicationExecutions(
                        registry, task.getHarborPolicyId());
                for (Map<String, Object> exec : executions) {
                    Long execId = toLong(exec.get("id"));
                    if (execId != null && execId.equals(task.getHarborExecutionId())) {
                        String status = (String) exec.get("status");
                        if ("Succeed".equalsIgnoreCase(status)) {
                            int total = exec.get("total") instanceof Number ? ((Number) exec.get("total")).intValue() : 0;
                            int succeed = exec.get("succeed") instanceof Number ? ((Number) exec.get("succeed")).intValue() : 0;
                            if (total == 0) {
                                task.setStatus("failed");
                                task.setStatusDetail("Replication completed but 0 artifacts transferred — check image name/tag and Harbor endpoint connectivity");
                            } else {
                                task.setStatus("success");
                                task.setStatusDetail("Replication completed: " + succeed + "/" + total + " artifacts transferred");
                            }
                            syncTaskMapper.update(task);
                        } else if ("Failed".equalsIgnoreCase(status)) {
                            task.setStatus("failed");
                            task.setStatusDetail("Replication failed: " + exec.get("status_text"));
                            syncTaskMapper.update(task);
                        } else if ("Stopped".equalsIgnoreCase(status)) {
                            task.setStatus("cancelled");
                            task.setStatusDetail("Replication stopped");
                            syncTaskMapper.update(task);
                        }
                        // else still InProgress, keep running
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to refresh sync task {}: {}", task.getId(), e.getMessage());
            }
        }
    }

    // ==================== Private helpers ====================

    private long ensureEndpoint(Registry registry, ImageRef ref) {
        // Check cache first
        Long cached = dockerHubEndpointCache.get(registry.getId());
        if (cached != null) {
            return cached;
        }

        // Check existing endpoints in Harbor
        List<Map<String, Object>> endpoints = registryApiService.listEndpoints(registry);
        String endpointName = getEndpointName(ref.registry);
        String endpointUrl = getEndpointUrl(ref.registry);

        for (Map<String, Object> ep : endpoints) {
            if (endpointName.equals(ep.get("name")) || endpointUrl.equals(ep.get("url"))) {
                Long id = toLong(ep.get("id"));
                if (id != null) {
                    dockerHubEndpointCache.put(registry.getId(), id);
                    return id;
                }
            }
        }

        // Create new endpoint
        Map<String, Object> epData = new HashMap<>();
        epData.put("name", endpointName);
        epData.put("url", endpointUrl);
        epData.put("type", getEndpointType(ref.registry));
        epData.put("insecure", false);
        Map<String, Object> credential = new HashMap<>();
        credential.put("type", "basic");
        credential.put("access_key", "");
        credential.put("access_secret", "");
        epData.put("credential", credential);

        Map<String, Object> result = registryApiService.createEndpoint(registry, epData);
        Long id = toLong(result.get("id"));

        // If no ID in response, search for it
        if (id == null) {
            endpoints = registryApiService.listEndpoints(registry);
            for (Map<String, Object> ep : endpoints) {
                if (endpointName.equals(ep.get("name"))) {
                    id = toLong(ep.get("id"));
                    break;
                }
            }
        }

        if (id != null) {
            dockerHubEndpointCache.put(registry.getId(), id);
            return id;
        }
        throw new RuntimeException("无法创建或获取外部仓库端点: " + endpointName);
    }

    /**
     * Calculate dest_namespace_replace_count based on repository path depth.
     * e.g. "etcd" → 0, "coredns/coredns" → 1, "a/b/c" → 2
     */
    private int calcReplaceCount(String repository) {
        if (repository == null || !repository.contains("/")) {
            return 0;
        }
        return (int) repository.chars().filter(c -> c == '/').count();
    }

    private Map<String, Object> createOneShotPolicy(Registry registry, ImageRef ref,
                                                      long endpointId, String targetProject) {
        String policyName = buildPolicyName(ref);
        int replaceCount = calcReplaceCount(ref.repository);

        // Build filters for this sync request
        List<Map<String, Object>> filters = new ArrayList<>();
        Map<String, Object> nameFilter = new HashMap<>();
        nameFilter.put("type", "name");
        nameFilter.put("value", ref.repository);
        filters.add(nameFilter);

        // Always add a tag filter: explicit tag or "latest" as fallback to avoid pulling ALL versions
        Map<String, Object> tagFilter = new HashMap<>();
        tagFilter.put("type", "tag");
        tagFilter.put("value", ref.tag != null && !ref.tag.isEmpty() ? ref.tag : "latest");
        filters.add(tagFilter);

        // Check if policy already exists (search by name to avoid pagination issues)
        List<Map<String, Object>> existing = registryApiService.listReplicationPoliciesByName(registry, policyName);
        for (Map<String, Object> p : existing) {
            if (policyName.equals(p.get("name"))) {
                // Update filters on existing policy to match current request
                Long existingId = toLong(p.get("id"));
                if (existingId != null) {
                    Map<String, Object> update = new HashMap<>();
                    update.put("name", policyName);
                    update.put("filters", filters);
                    update.put("src_registry", p.get("src_registry"));
                    update.put("dest_namespace", targetProject);
                    update.put("dest_namespace_replace_count", replaceCount);
                    Map<String, Object> trigger = new HashMap<>();
                    trigger.put("type", "manual");
                    update.put("trigger", trigger);
                    update.put("enabled", true);
                    registryApiService.updateReplicationPolicy(registry, existingId, update);
                }
                return p;
            }
        }

        // Create new policy, handle 409 Conflict (race condition or pagination miss)
        Map<String, Object> policy = new HashMap<>();
        policy.put("name", policyName);
        policy.put("description", "Auto-created quick sync policy for " + ref.fullImage);
        policy.put("enabled", true);

        Map<String, Object> srcRegistry = new HashMap<>();
        srcRegistry.put("id", endpointId);
        policy.put("src_registry", srcRegistry);

        if (targetProject != null && !targetProject.isEmpty()) {
            policy.put("dest_namespace", targetProject);
            policy.put("dest_namespace_replace_count", replaceCount);
        }

        policy.put("filters", filters);

        Map<String, Object> trigger = new HashMap<>();
        trigger.put("type", "manual");
        policy.put("trigger", trigger);

        try {
            return registryApiService.createReplicationPolicy(registry, policy);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("409")) {
                // Policy already exists — look it up by name
                log.info("Policy {} already exists (409), looking up by name", policyName);
                List<Map<String, Object>> retryList = registryApiService.listReplicationPoliciesByName(registry, policyName);
                for (Map<String, Object> p : retryList) {
                    if (policyName.equals(p.get("name"))) {
                        return p;
                    }
                }
            }
            throw e;
        }
    }

    private String buildTargetImage(Registry registry, String sourceImage, String targetProject) {
        ImageRef ref = parseImageRef(sourceImage);
        String host = registry.getHost();
        Integer port = registry.getServicePort();
        String hostPort = port != null && port != 80 && port != 443 ? host + ":" + port : host;
        String project = targetProject != null ? targetProject : "library";
        String repoName = ref.repository.contains("/") ?
                ref.repository.substring(ref.repository.lastIndexOf('/') + 1) : ref.repository;
        String tag = ref.tag != null ? ref.tag : "latest";
        return hostPort + "/" + project + "/" + repoName + ":" + tag;
    }

    private String buildPolicyName(ImageRef ref) {
        String safeName = ref.fullImage.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safeName.length() > 60) {
            safeName = safeName.substring(0, 60);
        }
        return "quick-sync_" + safeName;
    }

    private String getEndpointName(String registry) {
        if (registry == null || registry.isEmpty() || "docker.io".equals(registry)) {
            return "Docker Hub";
        }
        if (registry.contains("gcr.io")) return "Google GCR";
        if (registry.contains("ghcr.io")) return "GitHub GHCR";
        if (registry.contains("quay.io")) return "Quay.io";
        if (registry.contains("registry.k8s.io")) return "K8s Registry";
        if (registry.contains("mcr.microsoft.com")) return "Microsoft MCR";
        return registry;
    }

    private String getEndpointUrl(String registry) {
        if (registry == null || registry.isEmpty() || "docker.io".equals(registry)) {
            return "https://hub.docker.com";
        }
        return "https://" + registry;
    }

    private String getEndpointType(String registry) {
        if (registry == null || registry.isEmpty() || "docker.io".equals(registry)) {
            return "docker-hub";
        }
        if (registry.contains("gcr.io")) return "google-gcr";
        if (registry.contains("ghcr.io")) return "github-ghcr";
        if (registry.contains("quay.io")) return "quay";
        return "docker-registry";
    }

    static ImageRef parseImageRef(String image) {
        ImageRef ref = new ImageRef();
        ref.fullImage = image;

        String remaining = image;
        // Extract tag
        int colonIdx = remaining.lastIndexOf(':');
        if (colonIdx > 0 && !remaining.substring(colonIdx).contains("/")) {
            ref.tag = remaining.substring(colonIdx + 1);
            remaining = remaining.substring(0, colonIdx);
        } else {
            ref.tag = null; // No explicit tag — don't filter by tag in replication
        }

        // Extract registry (if contains '.' or ':' in first segment)
        int slashIdx = remaining.indexOf('/');
        if (slashIdx > 0) {
            String firstPart = remaining.substring(0, slashIdx);
            if (firstPart.contains(".") || firstPart.contains(":")) {
                ref.registry = firstPart;
                ref.repository = remaining.substring(slashIdx + 1);
            } else {
                // No explicit registry, assume Docker Hub
                ref.registry = "docker.io";
                ref.repository = remaining;
            }
        } else {
            // Single name like "nginx", assume Docker Hub library
            ref.registry = "docker.io";
            ref.repository = "library/" + remaining;
        }

        return ref;
    }

    static class ImageRef {
        String fullImage;
        String registry;
        String repository;
        String tag;
    }

    // ==================== Batch sync ====================

    @Override
    public List<SyncTask> batchSync(Long registryId, List<String> images, String targetProject, String syncMethod) {
        List<SyncTask> tasks = new ArrayList<>();
        for (String image : images) {
            String trimmed = image.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            try {
                SyncTask task;
                if ("skopeo".equals(syncMethod)) {
                    task = skopeoSync(registryId, trimmed, targetProject);
                } else {
                    task = quickSync(registryId, trimmed, targetProject);
                }
                tasks.add(task);
            } catch (Exception e) {
                log.error("Batch sync failed for image {}: {}", trimmed, e.getMessage());
                // Create a failed task record so user can see which images failed
                SyncTask failedTask = new SyncTask();
                failedTask.setRegistryId(registryId);
                failedTask.setSourceImage(trimmed);
                failedTask.setTargetProject(targetProject);
                failedTask.setSyncMethod(syncMethod != null ? syncMethod : "harbor_replication");
                failedTask.setStatus("failed");
                failedTask.setStatusDetail("提交失败: " + e.getMessage());
                syncTaskMapper.insert(failedTask);
                tasks.add(failedTask);
            }
        }
        return tasks;
    }

    @Override
    public List<String> extractImagesFromYaml(String yamlContent) {
        Set<String> images = new LinkedHashSet<>();
        if (yamlContent == null || yamlContent.isBlank()) return new ArrayList<>();

        // Parse "image:" fields from YAML - handles both quoted and unquoted values
        // Matches patterns like:
        //   image: nginx:1.25
        //   image: "registry.k8s.io/coredns/coredns:v1.11.1"
        //   - image: gcr.io/google-samples/hello-app:1.0
        for (String line : yamlContent.split("\n")) {
            String trimmed = line.trim();
            // Match "image:" key-value
            if (trimmed.startsWith("image:") || trimmed.startsWith("- image:")) {
                String value = trimmed.contains("image:") ?
                        trimmed.substring(trimmed.indexOf("image:") + 6).trim() : "";
                // Remove quotes
                value = value.replace("\"", "").replace("'", "").trim();
                // Skip template variables like {{ .Values.image }}
                if (!value.isEmpty() && !value.contains("{{") && !value.startsWith("#")) {
                    images.add(value);
                }
            }
        }
        return new ArrayList<>(images);
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
