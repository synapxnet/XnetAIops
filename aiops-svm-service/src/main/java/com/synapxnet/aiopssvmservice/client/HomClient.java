package com.synapxnet.aiopssvmservice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Component
public class HomClient {

    private static final Logger log = LoggerFactory.getLogger(HomClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${hom.service.url:http://localhost:9182}")
    private String homBaseUrl;

    public HomClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Get host info by ID from HOM service.
     */
    public HostInfo getHost(Long hostId) {
        String url = homBaseUrl + "/api/hom/hosts/" + hostId;
        try {
            String json = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.has("data") ? root.get("data") : root;
            return objectMapper.treeToValue(data, HostInfo.class);
        } catch (Exception e) {
            log.error("Failed to get host {} from HOM: {}", hostId, e.getMessage());
            throw new RuntimeException("Failed to get host info from HOM service: " + e.getMessage(), e);
        }
    }

    /**
     * Get all hosts for a cluster from HOM service.
     */
    public List<HostInfo> getHostsByCluster(Long clusterId) {
        String url = homBaseUrl + "/api/hom/hosts?clusterId=" + clusterId;
        try {
            String json = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.has("data") ? root.get("data") : root;
            List<HostInfo> hosts = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode node : data) {
                    hosts.add(objectMapper.treeToValue(node, HostInfo.class));
                }
            }
            return hosts;
        } catch (Exception e) {
            log.error("Failed to get hosts for cluster {} from HOM: {}", clusterId, e.getMessage());
            throw new RuntimeException("Failed to get hosts from HOM service: " + e.getMessage(), e);
        }
    }
}
