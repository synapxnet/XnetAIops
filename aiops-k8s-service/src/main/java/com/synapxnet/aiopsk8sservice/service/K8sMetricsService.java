package com.synapxnet.aiopsk8sservice.service;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.NodeMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.NodeMetricsList;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetricsList;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class K8sMetricsService {

    private static final Logger log = LoggerFactory.getLogger(K8sMetricsService.class);

    private final K8sClientFactory clientFactory;

    public K8sMetricsService(K8sClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    public Map<String, Object> getClusterMetrics(Long clusterId) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        Map<String, Object> metrics = new HashMap<>();

        // Get node metrics from metrics-server
        NodeMetricsList nodeMetricsList = client.top().nodes().metrics();
        double totalCpuUsed = 0;
        long totalMemoryUsed = 0;

        List<Map<String, Object>> nodeMetricsDetails = new ArrayList<>();
        for (NodeMetrics nm : nodeMetricsList.getItems()) {
            Map<String, Object> nodeMetric = new HashMap<>();
            nodeMetric.put("nodeName", nm.getMetadata().getName());

            Map<String, Quantity> usage = nm.getUsage();
            double cpuUsed = parseCpuNano(usage.get("cpu"));
            long memoryUsed = parseMemoryBytes(usage.get("memory"));

            nodeMetric.put("cpuUsed", cpuUsed);
            nodeMetric.put("memoryUsed", memoryUsed);
            nodeMetricsDetails.add(nodeMetric);

            totalCpuUsed += cpuUsed;
            totalMemoryUsed += memoryUsed;
        }

        // Get capacity from node status
        List<Node> nodes = client.nodes().list().getItems();
        double totalCpuCapacity = 0;
        long totalMemoryCapacity = 0;
        int totalPodCapacity = 0;
        long totalStorageCapacity = 0;

        for (Node node : nodes) {
            Map<String, Quantity> capacity = node.getStatus().getCapacity();
            if (capacity != null) {
                totalCpuCapacity += parseCpuNano(capacity.get("cpu"));
                totalMemoryCapacity += parseMemoryBytes(capacity.get("memory"));
                totalPodCapacity += parseIntQuantity(capacity.get("pods"));
                if (capacity.get("ephemeral-storage") != null) {
                    totalStorageCapacity += parseMemoryBytes(capacity.get("ephemeral-storage"));
                }
            }
        }

        // Pod count
        int podCount = client.pods().inAnyNamespace().list().getItems().size();

        metrics.put("cpuCapacity", totalCpuCapacity);
        metrics.put("cpuUsed", totalCpuUsed);
        metrics.put("memoryCapacity", totalMemoryCapacity);
        metrics.put("memoryUsed", totalMemoryUsed);
        metrics.put("podCapacity", totalPodCapacity);
        metrics.put("podUsed", podCount);
        metrics.put("storageCapacity", totalStorageCapacity);
        metrics.put("storageUsed", 0L);
        metrics.put("nodeMetrics", nodeMetricsDetails);

        return metrics;
    }

    public List<Map<String, Object>> getNodeRanking(Long clusterId, String sortBy, int limit) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        List<Node> nodes = client.nodes().list().getItems();

        NodeMetricsList nodeMetricsList;
        try {
            nodeMetricsList = client.top().nodes().metrics();
        } catch (Exception e) {
            log.warn("Metrics server unavailable for cluster {}", clusterId);
            return Collections.emptyList();
        }

        Map<String, NodeMetrics> metricsMap = new HashMap<>();
        for (NodeMetrics nm : nodeMetricsList.getItems()) {
            metricsMap.put(nm.getMetadata().getName(), nm);
        }

        List<Map<String, Object>> ranking = new ArrayList<>();
        for (Node node : nodes) {
            String nodeName = node.getMetadata().getName();
            Map<String, Object> entry = new HashMap<>();
            entry.put("name", nodeName);

            // Find IP
            if (node.getStatus().getAddresses() != null) {
                node.getStatus().getAddresses().stream()
                        .filter(a -> "InternalIP".equals(a.getType()))
                        .findFirst()
                        .ifPresent(a -> entry.put("ip", a.getAddress()));
            }

            // Roles
            List<String> roles = new ArrayList<>();
            if (node.getMetadata().getLabels() != null) {
                node.getMetadata().getLabels().keySet().stream()
                        .filter(k -> k.startsWith("node-role.kubernetes.io/"))
                        .map(k -> k.substring("node-role.kubernetes.io/".length()))
                        .forEach(roles::add);
            }
            entry.put("roles", roles.isEmpty() ? List.of("worker") : roles);

            Map<String, Quantity> capacity = node.getStatus().getCapacity();
            double cpuCap = capacity != null ? parseCpuNano(capacity.get("cpu")) : 0;
            long memCap = capacity != null ? parseMemoryBytes(capacity.get("memory")) : 0;
            int podCap = capacity != null ? parseIntQuantity(capacity.get("pods")) : 0;

            NodeMetrics nm = metricsMap.get(nodeName);
            double cpuUsed = 0;
            long memUsed = 0;
            if (nm != null && nm.getUsage() != null) {
                cpuUsed = parseCpuNano(nm.getUsage().get("cpu"));
                memUsed = parseMemoryBytes(nm.getUsage().get("memory"));
            }

            // Pod count on this node
            int podCount = (int) client.pods().inAnyNamespace()
                    .withField("spec.nodeName", nodeName).list().getItems().stream().count();

            double cpuPercent = cpuCap > 0 ? (cpuUsed / cpuCap) * 100 : 0;
            double memPercent = memCap > 0 ? ((double) memUsed / memCap) * 100 : 0;
            double podPercent = podCap > 0 ? ((double) podCount / podCap) * 100 : 0;

            entry.put("cpuCapacity", cpuCap);
            entry.put("cpuUsed", cpuUsed);
            entry.put("cpuPercent", Math.round(cpuPercent * 100.0) / 100.0);
            entry.put("memoryCapacity", memCap);
            entry.put("memoryUsed", memUsed);
            entry.put("memoryPercent", Math.round(memPercent * 100.0) / 100.0);
            entry.put("podCapacity", podCap);
            entry.put("podUsed", podCount);
            entry.put("podPercent", Math.round(podPercent * 100.0) / 100.0);

            ranking.add(entry);
        }

        // Sort
        Comparator<Map<String, Object>> comparator = switch (sortBy != null ? sortBy : "cpu") {
            case "memory" -> Comparator.comparingDouble(m -> -((Number) m.get("memoryPercent")).doubleValue());
            case "pod", "pods" -> Comparator.comparingDouble(m -> -((Number) m.get("podPercent")).doubleValue());
            default -> Comparator.comparingDouble(m -> -((Number) m.get("cpuPercent")).doubleValue());
        };

        ranking.sort(comparator);
        return ranking.stream().limit(limit).toList();
    }

    private double parseCpuNano(Quantity quantity) {
        if (quantity == null) return 0;
        String amount = quantity.getAmount();
        try {
            if (amount.endsWith("n")) {
                return Double.parseDouble(amount.replace("n", "")) / 1_000_000_000.0;
            } else if (amount.endsWith("m")) {
                return Double.parseDouble(amount.replace("m", "")) / 1000.0;
            }
            return Double.parseDouble(amount);
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseMemoryBytes(Quantity quantity) {
        if (quantity == null) return 0;
        String amount = quantity.getAmount();
        try {
            if (amount.endsWith("Ki")) return (long) (Double.parseDouble(amount.replace("Ki", "")) * 1024);
            if (amount.endsWith("Mi")) return (long) (Double.parseDouble(amount.replace("Mi", "")) * 1024 * 1024);
            if (amount.endsWith("Gi")) return (long) (Double.parseDouble(amount.replace("Gi", "")) * 1024 * 1024 * 1024);
            if (amount.endsWith("Ti")) return (long) (Double.parseDouble(amount.replace("Ti", "")) * 1024L * 1024 * 1024 * 1024);
            return Long.parseLong(amount.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private int parseIntQuantity(Quantity quantity) {
        if (quantity == null) return 0;
        try {
            return Integer.parseInt(quantity.getAmount());
        } catch (Exception e) {
            return 0;
        }
    }
}
