package com.synapxnet.aiopsregservice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class K8sHelmClient {

    private static final Logger log = LoggerFactory.getLogger(K8sHelmClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${k8s.service.url:http://localhost:9186}")
    private String k8sBaseUrl;

    public K8sHelmClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Install a Helm release via K8S service.
     * POST /api/k8s/clusters/{clusterId}/helm/releases
     */
    public Map<String, Object> installRelease(Long clusterId, String namespace, String releaseName,
                                               String chartName, String chartVersion, Long repoId, String values) {
        String url = k8sBaseUrl + "/api/k8s/clusters/" + clusterId + "/helm/releases";
        Map<String, Object> body = new HashMap<>();
        body.put("namespace", namespace);
        body.put("releaseName", releaseName);
        body.put("chartName", chartName);
        body.put("chartVersion", chartVersion);
        body.put("repoId", repoId);
        body.put("values", values);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String json = restTemplate.postForObject(url, request, String.class);
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.has("data") ? root.get("data") : root;
            return objectMapper.convertValue(data, Map.class);
        } catch (Exception e) {
            log.error("Failed to install Helm release: {}", e.getMessage());
            throw new RuntimeException("Failed to install Helm release: " + e.getMessage(), e);
        }
    }

    /**
     * Upgrade a Helm release via K8S service.
     * PUT /api/k8s/clusters/{clusterId}/helm/releases/{releaseName}
     */
    public Map<String, Object> upgradeRelease(Long clusterId, String releaseName, String namespace,
                                               String chartVersion, String values) {
        String url = k8sBaseUrl + "/api/k8s/clusters/" + clusterId + "/helm/releases/" + releaseName;
        Map<String, Object> body = new HashMap<>();
        body.put("namespace", namespace);
        body.put("chartVersion", chartVersion);
        body.put("values", values);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.put(url, request);
            return Map.of("status", "upgraded");
        } catch (Exception e) {
            log.error("Failed to upgrade Helm release: {}", e.getMessage());
            throw new RuntimeException("Failed to upgrade Helm release: " + e.getMessage(), e);
        }
    }

    /**
     * Uninstall a Helm release via K8S service.
     * DELETE /api/k8s/clusters/{clusterId}/helm/releases/{releaseName}?namespace=
     */
    public void uninstallRelease(Long clusterId, String releaseName, String namespace) {
        String url = k8sBaseUrl + "/api/k8s/clusters/" + clusterId + "/helm/releases/" + releaseName + "?namespace=" + namespace;
        try {
            restTemplate.delete(url);
        } catch (Exception e) {
            log.error("Failed to uninstall Helm release: {}", e.getMessage());
            throw new RuntimeException("Failed to uninstall Helm release: " + e.getMessage(), e);
        }
    }
}
