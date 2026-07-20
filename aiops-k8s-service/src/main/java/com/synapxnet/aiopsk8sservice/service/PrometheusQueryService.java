package com.synapxnet.aiopsk8sservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapxnet.aiopsk8sservice.entity.K8sPrometheusConfig;
import com.synapxnet.aiopsk8sservice.mapper.K8sPrometheusConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

@Service
public class PrometheusQueryService {

    private static final Logger log = LoggerFactory.getLogger(PrometheusQueryService.class);
    private final K8sPrometheusConfigMapper configMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public PrometheusQueryService(K8sPrometheusConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    /**
     * Execute an instant query: /api/v1/query
     */
    public JsonNode query(Long clusterId, String promql) {
        K8sPrometheusConfig config = getConfig(clusterId);
        String url = UriComponentsBuilder.fromHttpUrl(config.getPrometheusUrl())
                .path("/api/v1/query")
                .queryParam("query", promql)
                .toUriString();
        return executeGet(url, config);
    }

    /**
     * Execute a range query: /api/v1/query_range
     */
    public JsonNode queryRange(Long clusterId, String promql, long start, long end, String step) {
        K8sPrometheusConfig config = getConfig(clusterId);
        String url = UriComponentsBuilder.fromHttpUrl(config.getPrometheusUrl())
                .path("/api/v1/query_range")
                .queryParam("query", promql)
                .queryParam("start", start)
                .queryParam("end", end)
                .queryParam("step", step)
                .toUriString();
        return executeGet(url, config);
    }

    /**
     * Get instant value as double (first result)
     */
    public Double queryScalar(Long clusterId, String promql) {
        try {
            JsonNode result = query(clusterId, promql);
            JsonNode data = result.path("data").path("result");
            if (data.isArray() && !data.isEmpty()) {
                JsonNode value = data.get(0).path("value");
                if (value.isArray() && value.size() > 1) {
                    return Double.parseDouble(value.get(1).asText("0"));
                }
            }
        } catch (Exception e) {
            log.warn("Prometheus query failed for '{}': {}", promql, e.getMessage());
        }
        return null;
    }

    /**
     * Get multiple instant values as list of maps
     */
    public List<Map<String, Object>> queryVector(Long clusterId, String promql) {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            JsonNode result = query(clusterId, promql);
            JsonNode data = result.path("data").path("result");
            if (data.isArray()) {
                for (JsonNode item : data) {
                    Map<String, Object> entry = new HashMap<>();
                    JsonNode metric = item.path("metric");
                    metric.fields().forEachRemaining(f -> entry.put(f.getKey(), f.getValue().asText()));
                    JsonNode value = item.path("value");
                    if (value.isArray() && value.size() > 1) {
                        entry.put("value", Double.parseDouble(value.get(1).asText("0")));
                        entry.put("timestamp", value.get(0).asLong());
                    }
                    results.add(entry);
                }
            }
        } catch (Exception e) {
            log.warn("Prometheus vector query failed for '{}': {}", promql, e.getMessage());
        }
        return results;
    }

    /**
     * Get range query results as time series
     */
    public List<Map<String, Object>> queryRangeAsList(Long clusterId, String promql, long start, long end, String step) {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            JsonNode result = queryRange(clusterId, promql, start, end, step);
            JsonNode data = result.path("data").path("result");
            if (data.isArray()) {
                for (JsonNode item : data) {
                    Map<String, Object> series = new HashMap<>();
                    JsonNode metric = item.path("metric");
                    metric.fields().forEachRemaining(f -> series.put(f.getKey(), f.getValue().asText()));
                    JsonNode values = item.path("values");
                    List<double[]> points = new ArrayList<>();
                    if (values.isArray()) {
                        for (JsonNode v : values) {
                            if (v.isArray() && v.size() > 1) {
                                points.add(new double[]{v.get(0).asDouble(), Double.parseDouble(v.get(1).asText("0"))});
                            }
                        }
                    }
                    series.put("values", points);
                    results.add(series);
                }
            }
        } catch (Exception e) {
            log.warn("Prometheus range query failed for '{}': {}", promql, e.getMessage());
        }
        return results;
    }

    /**
     * Test connectivity to Prometheus
     */
    public boolean testConnection(Long clusterId) {
        try {
            K8sPrometheusConfig config = getConfig(clusterId);
            String url = config.getPrometheusUrl() + "/api/v1/status/config";
            executeGet(url, config);
            return true;
        } catch (Exception e) {
            log.warn("Prometheus connection test failed: {}", e.getMessage());
            return false;
        }
    }

    private K8sPrometheusConfig getConfig(Long clusterId) {
        K8sPrometheusConfig config = configMapper.findByClusterId(clusterId);
        if (config == null) {
            throw new RuntimeException("Prometheus not configured for cluster: " + clusterId);
        }
        return config;
    }

    private JsonNode executeGet(String url, K8sPrometheusConfig config) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if ("bearer".equalsIgnoreCase(config.getAuthType()) && config.getAuthToken() != null) {
                headers.setBearerAuth(config.getAuthToken());
            } else if ("basic".equalsIgnoreCase(config.getAuthType()) && config.getUsername() != null) {
                headers.setBasicAuth(config.getUsername(), config.getPassword());
            }
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Prometheus query failed: " + e.getMessage(), e);
        }
    }
}
