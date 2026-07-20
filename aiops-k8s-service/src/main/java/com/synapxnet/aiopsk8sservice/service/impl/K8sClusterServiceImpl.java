package com.synapxnet.aiopsk8sservice.service.impl;

import com.synapxnet.aiopsk8sservice.entity.K8sCluster;
import com.synapxnet.aiopsk8sservice.entity.K8sClusterComponent;
import com.synapxnet.aiopsk8sservice.entity.K8sClusterMetricsSnapshot;
import com.synapxnet.aiopsk8sservice.exception.ClusterConnectionException;
import com.synapxnet.aiopsk8sservice.exception.K8sResourceNotFoundException;
import com.synapxnet.aiopsk8sservice.mapper.K8sClusterComponentMapper;
import com.synapxnet.aiopsk8sservice.mapper.K8sClusterMapper;
import com.synapxnet.aiopsk8sservice.mapper.K8sClusterMetricsMapper;
import com.synapxnet.aiopsk8sservice.service.K8sClientFactory;
import com.synapxnet.aiopsk8sservice.service.K8sClusterService;
import com.synapxnet.aiopsk8sservice.service.K8sMetricsService;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.VersionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class K8sClusterServiceImpl implements K8sClusterService {

    private static final Logger log = LoggerFactory.getLogger(K8sClusterServiceImpl.class);

    private final K8sClusterMapper clusterMapper;
    private final K8sClusterComponentMapper componentMapper;
    private final K8sClusterMetricsMapper metricsMapper;
    private final K8sClientFactory clientFactory;
    private final K8sMetricsService metricsService;

    public K8sClusterServiceImpl(K8sClusterMapper clusterMapper,
                                  K8sClusterComponentMapper componentMapper,
                                  K8sClusterMetricsMapper metricsMapper,
                                  K8sClientFactory clientFactory,
                                  K8sMetricsService metricsService) {
        this.clusterMapper = clusterMapper;
        this.componentMapper = componentMapper;
        this.metricsMapper = metricsMapper;
        this.clientFactory = clientFactory;
        this.metricsService = metricsService;
    }

    @Override
    public List<K8sCluster> listAll() {
        return clusterMapper.findAll();
    }

    @Override
    public K8sCluster getById(Long id) {
        K8sCluster cluster = clusterMapper.findById(id);
        if (cluster == null) {
            throw new K8sResourceNotFoundException("Cluster not found: " + id);
        }
        return cluster;
    }

    @Override
    public K8sCluster create(K8sCluster cluster, String rawKubeconfig) {
        cluster.setUid(UUID.randomUUID().toString());
        cluster.setKubeconfigContent(clientFactory.encryptKubeconfig(rawKubeconfig));
        cluster.setStatus("connecting");

        // Test connection and get cluster info
        try {
            KubernetesClient client = clientFactory.createClientFromKubeconfig(rawKubeconfig);
            VersionInfo versionInfo = client.getKubernetesVersion();
            cluster.setVersion(versionInfo.getGitVersion());

            List<Node> nodes = client.nodes().list().getItems();
            cluster.setNodeCount(nodes.size());

            List<Namespace> namespaces = client.namespaces().list().getItems();
            cluster.setNamespaceCount(namespaces.size());

            String apiServer = client.getConfiguration().getMasterUrl();
            cluster.setApiServerUrl(apiServer);
            cluster.setStatus("active");

            client.close();
        } catch (Exception e) {
            log.warn("Could not connect to cluster during creation: {}", e.getMessage());
            cluster.setStatus("error");
        }

        if (cluster.getNodeCount() == null) cluster.setNodeCount(0);
        if (cluster.getNamespaceCount() == null) cluster.setNamespaceCount(0);

        clusterMapper.insert(cluster);
        return cluster;
    }

    @Override
    public K8sCluster update(Long id, K8sCluster updated, String rawKubeconfig) {
        K8sCluster existing = getById(id);
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setProvider(updated.getProvider());

        // SSH fields
        existing.setSshHost(updated.getSshHost());
        existing.setSshPort(updated.getSshPort());
        existing.setSshUser(updated.getSshUser());
        existing.setSshPassword(updated.getSshPassword());
        existing.setSshKey(updated.getSshKey());

        if (rawKubeconfig != null && !rawKubeconfig.isBlank()) {
            existing.setKubeconfigContent(clientFactory.encryptKubeconfig(rawKubeconfig));
            clientFactory.removeClient(id);
        }

        clusterMapper.update(existing);
        return existing;
    }

    @Override
    public void updateSsh(K8sCluster cluster) {
        clusterMapper.updateSsh(cluster);
    }

    @Override
    public void delete(Long id) {
        getById(id);
        clientFactory.removeClient(id);
        clusterMapper.deleteById(id);
    }

    @Override
    public Map<String, Object> testConnection(String kubeconfigContent) {
        Map<String, Object> result = new HashMap<>();
        try {
            KubernetesClient client = clientFactory.createClientFromKubeconfig(kubeconfigContent);
            VersionInfo versionInfo = client.getKubernetesVersion();
            List<Node> nodes = client.nodes().list().getItems();

            result.put("connected", true);
            result.put("version", versionInfo.getGitVersion());
            result.put("platform", versionInfo.getPlatform());
            result.put("nodeCount", nodes.size());
            result.put("apiServerUrl", client.getConfiguration().getMasterUrl());

            client.close();
        } catch (Exception e) {
            result.put("connected", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @Override
    public Map<String, Object> getOverview(Long id) {
        K8sCluster cluster = getById(id);
        KubernetesClient client = clientFactory.getClient(id);

        Map<String, Object> overview = new HashMap<>();
        overview.put("cluster", cluster);

        try {
            // Node info
            List<Node> nodes = client.nodes().list().getItems();
            long readyNodes = nodes.stream().filter(this::isNodeReady).count();
            overview.put("totalNodes", nodes.size());
            overview.put("readyNodes", readyNodes);

            // Namespace count
            List<Namespace> namespaces = client.namespaces().list().getItems();
            overview.put("namespaceCount", namespaces.size());

            // Workload counts
            int deployments = client.apps().deployments().inAnyNamespace().list().getItems().size();
            int statefulSets = client.apps().statefulSets().inAnyNamespace().list().getItems().size();
            int daemonSets = client.apps().daemonSets().inAnyNamespace().list().getItems().size();
            overview.put("deploymentCount", deployments);
            overview.put("statefulSetCount", statefulSets);
            overview.put("daemonSetCount", daemonSets);

            // Pod count
            List<Pod> pods = client.pods().inAnyNamespace().list().getItems();
            long runningPods = pods.stream()
                    .filter(p -> p.getStatus() != null && "Running".equals(p.getStatus().getPhase()))
                    .count();
            overview.put("totalPods", pods.size());
            overview.put("runningPods", runningPods);

            // Service count
            int services = client.services().inAnyNamespace().list().getItems().size();
            overview.put("serviceCount", services);

            // Version
            VersionInfo version = client.getKubernetesVersion();
            overview.put("k8sVersion", version.getGitVersion());

            // Update cluster info in DB
            clusterMapper.updateClusterInfo(id, nodes.size(), namespaces.size(), version.getGitVersion());
            clusterMapper.updateStatus(id, "active");

            // Aggregate resource metrics from nodes
            double cpuCapacity = 0, cpuUsed = 0;
            long memoryCapacity = 0, memoryUsed = 0;
            int podCapacity = 0;
            long storageCapacity = 0;

            for (Node node : nodes) {
                Map<String, Quantity> capacity = node.getStatus().getCapacity();
                Map<String, Quantity> allocatable = node.getStatus().getAllocatable();
                if (capacity != null) {
                    cpuCapacity += parseCpu(capacity.get("cpu"));
                    memoryCapacity += parseMemory(capacity.get("memory"));
                    podCapacity += parseInt(capacity.get("pods"));
                    if (capacity.get("ephemeral-storage") != null) {
                        storageCapacity += parseMemory(capacity.get("ephemeral-storage"));
                    }
                }
            }

            overview.put("cpuCapacity", cpuCapacity);
            overview.put("memoryCapacity", memoryCapacity);
            overview.put("podCapacity", podCapacity);
            overview.put("podUsed", pods.size());
            overview.put("storageCapacity", storageCapacity);

            // Try to get metrics from metrics-server
            try {
                Map<String, Object> nodeMetrics = metricsService.getClusterMetrics(id);
                overview.putAll(nodeMetrics);
            } catch (Exception e) {
                log.debug("Metrics server not available for cluster {}: {}", id, e.getMessage());
            }

        } catch (Exception e) {
            log.error("Error getting overview for cluster {}: {}", id, e.getMessage());
            clusterMapper.updateStatus(id, "error");
            throw new ClusterConnectionException("Failed to get cluster overview: " + e.getMessage());
        }

        return overview;
    }

    @Override
    public List<K8sClusterComponent> getComponents(Long id) {
        KubernetesClient client = clientFactory.getClient(id);
        List<K8sClusterComponent> components = new ArrayList<>();

        try {
            // Check system pods in kube-system as component health indicators
            List<Pod> systemPods = client.pods().inNamespace("kube-system").list().getItems();
            Map<String, String> podStatusMap = new HashMap<>();
            for (Pod pod : systemPods) {
                String name = pod.getMetadata().getName();
                String componentName = name.replaceAll("-[a-z0-9]+-[a-z0-9]+$", "")
                        .replaceAll("-[a-z0-9]{5}$", "");
                String phase = pod.getStatus() != null ? pod.getStatus().getPhase() : "Unknown";
                // Keep worst status
                podStatusMap.merge(componentName, phase, (old, newVal) ->
                        "Running".equals(old) ? newVal : old);
            }

            for (Map.Entry<String, String> entry : podStatusMap.entrySet()) {
                K8sClusterComponent comp = new K8sClusterComponent();
                comp.setClusterId(id);
                comp.setComponentName(entry.getKey());
                comp.setComponentType("kubernetes");
                comp.setStatus("Running".equals(entry.getValue()) ? "healthy" : "unhealthy");
                comp.setMessage("Pod status: " + entry.getValue());
                components.add(comp);
            }

            // Persist components
            componentMapper.deleteByClusterId(id);
            for (K8sClusterComponent comp : components) {
                componentMapper.insert(comp);
            }

        } catch (Exception e) {
            log.error("Error getting components for cluster {}: {}", id, e.getMessage());
            // Return cached if available
            List<K8sClusterComponent> cached = componentMapper.findByClusterId(id);
            if (!cached.isEmpty()) return cached;
            throw new ClusterConnectionException("Failed to get cluster components: " + e.getMessage());
        }

        return components;
    }

    @Override
    public K8sClusterMetricsSnapshot getMetrics(Long id) {
        try {
            Map<String, Object> metrics = metricsService.getClusterMetrics(id);
            K8sClusterMetricsSnapshot snapshot = new K8sClusterMetricsSnapshot();
            snapshot.setClusterId(id);
            snapshot.setCpuCapacity((Double) metrics.getOrDefault("cpuCapacity", 0.0));
            snapshot.setCpuUsed((Double) metrics.getOrDefault("cpuUsed", 0.0));
            snapshot.setMemoryCapacity(((Number) metrics.getOrDefault("memoryCapacity", 0L)).longValue());
            snapshot.setMemoryUsed(((Number) metrics.getOrDefault("memoryUsed", 0L)).longValue());
            snapshot.setPodCapacity((Integer) metrics.getOrDefault("podCapacity", 0));
            snapshot.setPodUsed((Integer) metrics.getOrDefault("podUsed", 0));
            snapshot.setStorageCapacity(((Number) metrics.getOrDefault("storageCapacity", 0L)).longValue());
            snapshot.setStorageUsed(((Number) metrics.getOrDefault("storageUsed", 0L)).longValue());

            metricsMapper.insert(snapshot);
            return snapshot;
        } catch (Exception e) {
            // Return latest cached
            K8sClusterMetricsSnapshot cached = metricsMapper.findLatestByClusterId(id);
            if (cached != null) return cached;
            throw new ClusterConnectionException("Metrics unavailable: " + e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> getEvents(Long id, int limit) {
        KubernetesClient client = clientFactory.getClient(id);
        List<Map<String, Object>> events = new ArrayList<>();
        try {
            EventList eventList = client.v1().events().inAnyNamespace().list();
            List<Event> items = eventList.getItems();
            items.sort((a, b) -> {
                String aTime = a.getLastTimestamp() != null ? a.getLastTimestamp() : a.getEventTime() != null ? String.valueOf(a.getEventTime()) : "";
                String bTime = b.getLastTimestamp() != null ? b.getLastTimestamp() : b.getEventTime() != null ? String.valueOf(b.getEventTime()) : "";
                return bTime.compareTo(aTime);
            });

            int count = 0;
            for (Event event : items) {
                if (count >= limit) break;
                Map<String, Object> eventMap = new HashMap<>();
                eventMap.put("namespace", event.getMetadata().getNamespace());
                eventMap.put("type", event.getType());
                eventMap.put("reason", event.getReason());
                eventMap.put("message", event.getMessage());
                eventMap.put("kind", event.getInvolvedObject() != null ? event.getInvolvedObject().getKind() : "");
                eventMap.put("name", event.getInvolvedObject() != null ? event.getInvolvedObject().getName() : "");
                eventMap.put("lastTimestamp", event.getLastTimestamp());
                eventMap.put("count", event.getCount());
                eventMap.put("source", event.getSource() != null ? event.getSource().getComponent() : "");
                events.add(eventMap);
                count++;
            }
        } catch (Exception e) {
            log.error("Error getting events for cluster {}: {}", id, e.getMessage());
        }
        return events;
    }

    @Override
    public String getKubeconfig(Long id) {
        K8sCluster cluster = getById(id);
        return clientFactory.decryptKubeconfig(cluster.getKubeconfigContent());
    }

    private boolean isNodeReady(Node node) {
        if (node.getStatus() == null || node.getStatus().getConditions() == null) return false;
        return node.getStatus().getConditions().stream()
                .anyMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
    }

    private double parseCpu(Quantity quantity) {
        if (quantity == null) return 0;
        String value = quantity.getAmount();
        String format = quantity.getFormat();
        try {
            double num = Double.parseDouble(value);
            if (format != null && format.equals("m")) return num / 1000.0;
            if (value.endsWith("m")) return Double.parseDouble(value.replace("m", "")) / 1000.0;
            return num;
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseMemory(Quantity quantity) {
        if (quantity == null) return 0;
        String value = quantity.getAmount();
        try {
            double num = Double.parseDouble(value.replaceAll("[^0-9.]", ""));
            if (value.endsWith("Ki")) return (long) (num * 1024);
            if (value.endsWith("Mi")) return (long) (num * 1024 * 1024);
            if (value.endsWith("Gi")) return (long) (num * 1024 * 1024 * 1024);
            if (value.endsWith("Ti")) return (long) (num * 1024L * 1024 * 1024 * 1024);
            return (long) num;
        } catch (Exception e) {
            return 0;
        }
    }

    private int parseInt(Quantity quantity) {
        if (quantity == null) return 0;
        try {
            return Integer.parseInt(quantity.getAmount());
        } catch (Exception e) {
            return 0;
        }
    }
}
