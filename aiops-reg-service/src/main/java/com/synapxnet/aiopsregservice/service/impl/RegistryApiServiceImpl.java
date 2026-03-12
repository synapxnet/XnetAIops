package com.synapxnet.aiopsregservice.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapxnet.aiopsregservice.entity.Registry;
import com.synapxnet.aiopsregservice.service.RegistryApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class RegistryApiServiceImpl implements RegistryApiService {

    private static final Logger log = LoggerFactory.getLogger(RegistryApiServiceImpl.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public RegistryApiServiceImpl(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    // ==================== Probe ====================

    @Override
    public boolean probe(Registry registry) {
        try {
            String url = getApiBase(registry);
            switch (registry.getRegistryType()) {
                case "harbor":
                    url += "/health";
                    break;
                case "gitlab":
                    url += "/version";
                    break;
                case "docker_distribution":
                    url += "/v2/";
                    break;
                default:
                    return false;
            }
            HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders(registry));
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("Probe failed for registry {}: {}", registry.getId(), e.getMessage());
            return false;
        }
    }

    // ==================== Projects ====================

    @Override
    public List<Map<String, Object>> listProjects(Registry registry) {
        switch (registry.getRegistryType()) {
            case "harbor":
                return harborGet(registry, "/projects");
            case "gitlab":
                return gitlabGet(registry, "/groups");
            case "docker_distribution":
                return distributionCatalog(registry);
            default:
                return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> createProject(Registry registry, String projectName, boolean isPublic) {
        switch (registry.getRegistryType()) {
            case "harbor": {
                Map<String, Object> body = new HashMap<>();
                body.put("project_name", projectName);
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("public", isPublic ? "true" : "false");
                body.put("metadata", metadata);
                return harborPost(registry, "/projects", body);
            }
            case "gitlab": {
                Map<String, Object> body = new HashMap<>();
                body.put("name", projectName);
                body.put("path", projectName);
                body.put("visibility", isPublic ? "public" : "private");
                return gitlabPost(registry, "/groups", body);
            }
            default:
                throw new IllegalArgumentException("Create project not supported for " + registry.getRegistryType());
        }
    }

    @Override
    public void deleteProject(Registry registry, Object projectId) {
        switch (registry.getRegistryType()) {
            case "harbor":
                harborDelete(registry, "/projects/" + projectId);
                break;
            case "gitlab":
                gitlabDelete(registry, "/groups/" + projectId);
                break;
            default:
                throw new IllegalArgumentException("Delete project not supported for " + registry.getRegistryType());
        }
    }

    // ==================== Repositories ====================

    @Override
    public List<Map<String, Object>> listRepositories(Registry registry, String projectName) {
        switch (registry.getRegistryType()) {
            case "harbor":
                return harborGet(registry, "/projects/" + projectName + "/repositories");
            case "gitlab":
                return gitlabGet(registry, "/groups/" + projectName + "/registry/repositories");
            case "docker_distribution":
                return distributionCatalog(registry);
            default:
                return Collections.emptyList();
        }
    }

    // ==================== Tags ====================

    @Override
    public List<Map<String, Object>> listTags(Registry registry, String repoName) {
        switch (registry.getRegistryType()) {
            case "harbor": {
                // Harbor API v2: /projects/{project}/repositories/{repo}/artifacts
                String[] parts = splitRepoName(repoName);
                return harborGet(registry, "/projects/" + parts[0] + "/repositories/" + encodeSlash(parts[1]) + "/artifacts");
            }
            case "gitlab":
                return gitlabGet(registry, "/registry/repositories/" + repoName + "/tags");
            case "docker_distribution":
                return distributionTags(registry, repoName);
            default:
                return Collections.emptyList();
        }
    }

    @Override
    public void deleteTag(Registry registry, String repoName, String tag) {
        switch (registry.getRegistryType()) {
            case "harbor": {
                String[] parts = splitRepoName(repoName);
                harborDelete(registry, "/projects/" + parts[0] + "/repositories/" + encodeSlash(parts[1]) + "/artifacts/" + tag);
                break;
            }
            case "gitlab":
                gitlabDelete(registry, "/registry/repositories/" + repoName + "/tags/" + tag);
                break;
            case "docker_distribution":
                distributionDeleteManifest(registry, repoName, tag);
                break;
        }
    }

    @Override
    public Map<String, Object> getManifest(Registry registry, String repoName, String reference) {
        switch (registry.getRegistryType()) {
            case "harbor": {
                String[] parts = splitRepoName(repoName);
                List<Map<String, Object>> result = harborGet(registry,
                        "/projects/" + parts[0] + "/repositories/" + encodeSlash(parts[1]) + "/artifacts/" + reference);
                return result.isEmpty() ? Collections.emptyMap() : result.get(0);
            }
            case "docker_distribution": {
                String url = getApiBase(registry) + "/v2/" + repoName + "/manifests/" + reference;
                HttpHeaders headers = buildAuthHeaders(registry);
                headers.set("Accept", "application/vnd.docker.distribution.manifest.v2+json");
                HttpEntity<Void> entity = new HttpEntity<>(headers);
                try {
                    ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                    return objectMapper.readValue(resp.getBody(), Map.class);
                } catch (Exception e) {
                    log.error("Failed to get manifest: {}", e.getMessage());
                    return Collections.emptyMap();
                }
            }
            default:
                return Collections.emptyMap();
        }
    }

    // ==================== Users ====================

    @Override
    public List<Map<String, Object>> listUsers(Registry registry) {
        switch (registry.getRegistryType()) {
            case "harbor":
                return harborGet(registry, "/users");
            case "gitlab":
                return gitlabGet(registry, "/users");
            default:
                throw new IllegalArgumentException("User management not supported for " + registry.getRegistryType());
        }
    }

    @Override
    public Map<String, Object> createUser(Registry registry, Map<String, Object> userInfo) {
        switch (registry.getRegistryType()) {
            case "harbor":
                return harborPost(registry, "/users", userInfo);
            case "gitlab":
                return gitlabPost(registry, "/users", userInfo);
            default:
                throw new IllegalArgumentException("User management not supported for " + registry.getRegistryType());
        }
    }

    @Override
    public void updateUser(Registry registry, Object userId, Map<String, Object> userInfo) {
        switch (registry.getRegistryType()) {
            case "harbor":
                harborPut(registry, "/users/" + userId, userInfo);
                break;
            case "gitlab":
                gitlabPut(registry, "/users/" + userId, userInfo);
                break;
            default:
                throw new IllegalArgumentException("User management not supported for " + registry.getRegistryType());
        }
    }

    @Override
    public void deleteUser(Registry registry, Object userId) {
        switch (registry.getRegistryType()) {
            case "harbor":
                harborDelete(registry, "/users/" + userId);
                break;
            case "gitlab":
                gitlabDelete(registry, "/users/" + userId);
                break;
            default:
                throw new IllegalArgumentException("User management not supported for " + registry.getRegistryType());
        }
    }

    // ==================== Harbor helpers ====================

    private List<Map<String, Object>> harborGet(Registry registry, String path) {
        String url = getApiBase(registry) + "/api/v2.0" + path;
        HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders(registry));
        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return parseJsonArray(resp.getBody());
        } catch (Exception e) {
            log.error("Harbor GET {} failed: {}", path, e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<String, Object> harborPost(Registry registry, String path, Map<String, Object> body) {
        String url = getApiBase(registry) + "/api/v2.0" + path;
        HttpHeaders headers = buildAuthHeaders(registry);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (resp.getBody() != null && !resp.getBody().isEmpty()) {
                return objectMapper.readValue(resp.getBody(), Map.class);
            }
            // Harbor returns 201 with ID in Location header (e.g. /api/v2.0/replication/executions/123)
            Map<String, Object> result = new HashMap<>();
            result.put("status", "created");
            String location = resp.getHeaders().getFirst("Location");
            if (location != null && !location.isEmpty()) {
                String idStr = location.substring(location.lastIndexOf('/') + 1);
                try {
                    result.put("id", Long.parseLong(idStr));
                } catch (NumberFormatException ignored) {
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Harbor POST {} failed: {}", path, e.getMessage());
            throw new RuntimeException("Harbor API call failed: " + e.getMessage(), e);
        }
    }

    private void harborPut(Registry registry, String path, Map<String, Object> body) {
        String url = getApiBase(registry) + "/api/v2.0" + path;
        HttpHeaders headers = buildAuthHeaders(registry);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
    }

    private void harborDelete(Registry registry, String path) {
        String url = getApiBase(registry) + "/api/v2.0" + path;
        HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders(registry));
        restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
    }

    // ==================== GitLab helpers ====================

    private List<Map<String, Object>> gitlabGet(Registry registry, String path) {
        String url = getApiBase(registry) + "/api/v4" + path;
        HttpEntity<Void> entity = new HttpEntity<>(buildGitlabHeaders(registry));
        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return parseJsonArray(resp.getBody());
        } catch (Exception e) {
            log.error("GitLab GET {} failed: {}", path, e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<String, Object> gitlabPost(Registry registry, String path, Map<String, Object> body) {
        String url = getApiBase(registry) + "/api/v4" + path;
        HttpHeaders headers = buildGitlabHeaders(registry);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (resp.getBody() != null && !resp.getBody().isEmpty()) {
                return objectMapper.readValue(resp.getBody(), Map.class);
            }
            return Map.of("status", "created");
        } catch (Exception e) {
            log.error("GitLab POST {} failed: {}", path, e.getMessage());
            throw new RuntimeException("GitLab API call failed: " + e.getMessage(), e);
        }
    }

    private void gitlabPut(Registry registry, String path, Map<String, Object> body) {
        String url = getApiBase(registry) + "/api/v4" + path;
        HttpHeaders headers = buildGitlabHeaders(registry);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
    }

    private void gitlabDelete(Registry registry, String path) {
        String url = getApiBase(registry) + "/api/v4" + path;
        HttpEntity<Void> entity = new HttpEntity<>(buildGitlabHeaders(registry));
        restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
    }

    // ==================== Docker Distribution helpers ====================

    private List<Map<String, Object>> distributionCatalog(Registry registry) {
        String url = getApiBase(registry) + "/v2/_catalog";
        HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders(registry));
        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode repos = root.get("repositories");
            List<Map<String, Object>> result = new ArrayList<>();
            if (repos != null && repos.isArray()) {
                for (JsonNode repo : repos) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", repo.asText());
                    result.add(m);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Distribution catalog failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> distributionTags(Registry registry, String repoName) {
        String url = getApiBase(registry) + "/v2/" + repoName + "/tags/list";
        HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders(registry));
        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode tags = root.get("tags");
            List<Map<String, Object>> result = new ArrayList<>();
            if (tags != null && tags.isArray()) {
                for (JsonNode tag : tags) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", tag.asText());
                    result.add(m);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Distribution tags list failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private void distributionDeleteManifest(Registry registry, String repoName, String reference) {
        // First get the digest for the tag
        Map<String, Object> manifest = getManifest(registry, repoName, reference);
        String digest = manifest != null ? (String) manifest.get("digest") : reference;
        String url = getApiBase(registry) + "/v2/" + repoName + "/manifests/" + digest;
        HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders(registry));
        restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
    }

    // ==================== Artifacts (enhanced) ====================

    @Override
    public List<Map<String, Object>> listArtifacts(Registry registry, String projectName, String repoName) {
        switch (registry.getRegistryType()) {
            case "harbor":
                return harborGet(registry, "/projects/" + projectName + "/repositories/" + encodeSlash(repoName)
                        + "/artifacts?with_tag=true&with_scan_overview=true&with_label=true&page_size=50");
            case "docker_distribution":
                return distributionTags(registry, projectName + "/" + repoName);
            default:
                return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> getArtifactDetail(Registry registry, String repoName, String reference) {
        switch (registry.getRegistryType()) {
            case "harbor": {
                String[] parts = splitRepoName(repoName);
                List<Map<String, Object>> result = harborGet(registry,
                        "/projects/" + parts[0] + "/repositories/" + encodeSlash(parts[1])
                                + "/artifacts/" + reference + "?with_tag=true&with_scan_overview=true");
                return result.isEmpty() ? Collections.emptyMap() : result.get(0);
            }
            default:
                return getManifest(registry, repoName, reference);
        }
    }

    // ==================== Registry Endpoints (Harbor Replication) ====================

    @Override
    public List<Map<String, Object>> listEndpoints(Registry registry) {
        if (!"harbor".equals(registry.getRegistryType())) {
            throw new IllegalArgumentException("Registry endpoints management only supported for Harbor");
        }
        return harborGet(registry, "/registries");
    }

    @Override
    public Map<String, Object> createEndpoint(Registry registry, Map<String, Object> endpointData) {
        if (!"harbor".equals(registry.getRegistryType())) {
            throw new IllegalArgumentException("Registry endpoints management only supported for Harbor");
        }
        return harborPost(registry, "/registries", endpointData);
    }

    @Override
    public void updateEndpoint(Registry registry, Object endpointId, Map<String, Object> endpointData) {
        if (!"harbor".equals(registry.getRegistryType())) {
            throw new IllegalArgumentException("Registry endpoints management only supported for Harbor");
        }
        harborPut(registry, "/registries/" + endpointId, endpointData);
    }

    @Override
    public void deleteEndpoint(Registry registry, Object endpointId) {
        if (!"harbor".equals(registry.getRegistryType())) {
            throw new IllegalArgumentException("Registry endpoints management only supported for Harbor");
        }
        harborDelete(registry, "/registries/" + endpointId);
    }

    @Override
    public Map<String, Object> pingEndpoint(Registry registry, Map<String, Object> endpointData) {
        if (!"harbor".equals(registry.getRegistryType())) {
            throw new IllegalArgumentException("Registry endpoints management only supported for Harbor");
        }
        try {
            harborPost(registry, "/registries/ping", endpointData);
            return Map.of("status", "ok");
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    // ==================== Replication ====================

    @Override
    public List<Map<String, Object>> listReplicationPolicies(Registry registry) {
        if (!"harbor".equals(registry.getRegistryType())) {
            throw new IllegalArgumentException("Replication only supported for Harbor");
        }
        return harborGet(registry, "/replication/policies?page_size=100");
    }

    @Override
    public List<Map<String, Object>> listReplicationPoliciesByName(Registry registry, String name) {
        if (!"harbor".equals(registry.getRegistryType())) {
            throw new IllegalArgumentException("Replication only supported for Harbor");
        }
        return harborGet(registry, "/replication/policies?name=" + name);
    }

    @Override
    public Map<String, Object> createReplicationPolicy(Registry registry, Map<String, Object> policyData) {
        if (!"harbor".equals(registry.getRegistryType())) {
            throw new IllegalArgumentException("Replication only supported for Harbor");
        }
        return harborPost(registry, "/replication/policies", policyData);
    }

    @Override
    public Map<String, Object> getReplicationPolicy(Registry registry, Object policyId) {
        if (!"harbor".equals(registry.getRegistryType())) {
            throw new IllegalArgumentException("Replication only supported for Harbor");
        }
        List<Map<String, Object>> result = harborGet(registry, "/replication/policies/" + policyId);
        return result.isEmpty() ? Collections.emptyMap() : result.get(0);
    }

    @Override
    public void updateReplicationPolicy(Registry registry, Object policyId, Map<String, Object> policyData) {
        if (!"harbor".equals(registry.getRegistryType())) {
            throw new IllegalArgumentException("Replication only supported for Harbor");
        }
        harborPut(registry, "/replication/policies/" + policyId, policyData);
    }

    @Override
    public void deleteReplicationPolicy(Registry registry, Object policyId) {
        if (!"harbor".equals(registry.getRegistryType())) {
            throw new IllegalArgumentException("Replication only supported for Harbor");
        }
        harborDelete(registry, "/replication/policies/" + policyId);
    }

    @Override
    public Map<String, Object> triggerReplication(Registry registry, Map<String, Object> executionData) {
        if (!"harbor".equals(registry.getRegistryType())) {
            throw new IllegalArgumentException("Replication only supported for Harbor");
        }
        return harborPost(registry, "/replication/executions", executionData);
    }

    @Override
    public List<Map<String, Object>> listReplicationExecutions(Registry registry, Long policyId) {
        if (!"harbor".equals(registry.getRegistryType())) {
            throw new IllegalArgumentException("Replication only supported for Harbor");
        }
        String path = "/replication/executions";
        if (policyId != null) {
            path += "?policy_id=" + policyId;
        }
        return harborGet(registry, path);
    }

    @Override
    public List<Map<String, Object>> getReplicationTasks(Registry registry, Object executionId) {
        if (!"harbor".equals(registry.getRegistryType())) {
            throw new IllegalArgumentException("Replication only supported for Harbor");
        }
        return harborGet(registry, "/replication/executions/" + executionId + "/tasks");
    }

    // ==================== Common helpers ====================

    private String getApiBase(Registry registry) {
        if (registry.getApiUrl() != null && !registry.getApiUrl().isEmpty()) {
            return registry.getApiUrl().replaceAll("/+$", "");
        }
        if (registry.getEndpoint() != null && !registry.getEndpoint().isEmpty()) {
            return registry.getEndpoint().replaceAll("/+$", "");
        }
        String scheme = Boolean.TRUE.equals(registry.getUseSsl()) ? "https" : "http";
        return scheme + "://" + registry.getHost();
    }

    private HttpHeaders buildAuthHeaders(Registry registry) {
        HttpHeaders headers = new HttpHeaders();
        if (registry.getAdminUser() != null && registry.getEncryptedAdminPassword() != null) {
            String credentials = registry.getAdminUser() + ":" + registry.getEncryptedAdminPassword();
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
            headers.set("Authorization", "Basic " + encoded);
        }
        return headers;
    }

    private HttpHeaders buildGitlabHeaders(Registry registry) {
        HttpHeaders headers = new HttpHeaders();
        if (registry.getEncryptedAdminPassword() != null) {
            headers.set("PRIVATE-TOKEN", registry.getEncryptedAdminPassword());
        }
        return headers;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseJsonArray(String json) {
        try {
            if (json == null || json.isEmpty()) return Collections.emptyList();
            JsonNode root = objectMapper.readTree(json);
            if (root.isArray()) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonNode node : root) {
                    result.add(objectMapper.convertValue(node, Map.class));
                }
                return result;
            }
            // If it's an object, wrap it
            return List.of(objectMapper.convertValue(root, Map.class));
        } catch (Exception e) {
            log.error("Failed to parse JSON array: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String[] splitRepoName(String repoName) {
        int idx = repoName.indexOf('/');
        if (idx > 0) {
            return new String[]{repoName.substring(0, idx), repoName.substring(idx + 1)};
        }
        return new String[]{"library", repoName};
    }

    private String encodeSlash(String s) {
        return s.replace("/", "%2F");
    }
}
