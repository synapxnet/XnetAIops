package com.synapxnet.aiopsregservice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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
}
