package com.synapxnet.aiopsk8sservice.service.impl;

import com.synapxnet.aiopsk8sservice.exception.K8sResourceNotFoundException;
import com.synapxnet.aiopsk8sservice.service.K8sClientFactory;
import com.synapxnet.aiopsk8sservice.service.K8sNodeService;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class K8sNodeServiceImpl implements K8sNodeService {

    private static final Logger log = LoggerFactory.getLogger(K8sNodeServiceImpl.class);

    private final K8sClientFactory clientFactory;

    public K8sNodeServiceImpl(K8sClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public List<Map<String, Object>> listNodes(Long clusterId) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        List<Node> nodes = client.nodes().list().getItems();
        return nodes.stream().map(this::nodeToMap).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getNode(Long clusterId, String nodeName) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        Node node = client.nodes().withName(nodeName).get();
        if (node == null) {
            throw new K8sResourceNotFoundException("Node not found: " + nodeName);
        }
        Map<String, Object> result = nodeToMap(node);

        // Add detailed conditions
        List<Map<String, Object>> conditions = new ArrayList<>();
        if (node.getStatus().getConditions() != null) {
            for (NodeCondition condition : node.getStatus().getConditions()) {
                Map<String, Object> condMap = new HashMap<>();
                condMap.put("type", condition.getType());
                condMap.put("status", condition.getStatus());
                condMap.put("reason", condition.getReason());
                condMap.put("message", condition.getMessage());
                condMap.put("lastTransitionTime", condition.getLastTransitionTime());
                conditions.add(condMap);
            }
        }
        result.put("conditions", conditions);

        // Add taints
        List<Map<String, String>> taints = new ArrayList<>();
        if (node.getSpec().getTaints() != null) {
            for (Taint taint : node.getSpec().getTaints()) {
                Map<String, String> taintMap = new HashMap<>();
                taintMap.put("key", taint.getKey());
                taintMap.put("value", taint.getValue());
                taintMap.put("effect", taint.getEffect());
                taints.add(taintMap);
            }
        }
        result.put("taints", taints);

        // Add node info
        NodeSystemInfo nodeInfo = node.getStatus().getNodeInfo();
        if (nodeInfo != null) {
            Map<String, String> systemInfo = new HashMap<>();
            systemInfo.put("osImage", nodeInfo.getOsImage());
            systemInfo.put("kernelVersion", nodeInfo.getKernelVersion());
            systemInfo.put("containerRuntimeVersion", nodeInfo.getContainerRuntimeVersion());
            systemInfo.put("kubeletVersion", nodeInfo.getKubeletVersion());
            systemInfo.put("kubeProxyVersion", nodeInfo.getKubeProxyVersion());
            systemInfo.put("architecture", nodeInfo.getArchitecture());
            systemInfo.put("operatingSystem", nodeInfo.getOperatingSystem());
            result.put("systemInfo", systemInfo);
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> getNodePods(Long clusterId, String nodeName) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        List<Pod> pods = client.pods().inAnyNamespace()
                .withField("spec.nodeName", nodeName).list().getItems();

        return pods.stream().map(pod -> {
            Map<String, Object> podMap = new HashMap<>();
            podMap.put("name", pod.getMetadata().getName());
            podMap.put("namespace", pod.getMetadata().getNamespace());
            podMap.put("status", pod.getStatus().getPhase());
            podMap.put("restarts", getRestartCount(pod));
            podMap.put("createdAt", pod.getMetadata().getCreationTimestamp());

            List<String> containerImages = pod.getSpec().getContainers().stream()
                    .map(Container::getImage).collect(Collectors.toList());
            podMap.put("images", containerImages);

            int readyContainers = 0;
            int totalContainers = pod.getSpec().getContainers().size();
            if (pod.getStatus().getContainerStatuses() != null) {
                readyContainers = (int) pod.getStatus().getContainerStatuses().stream()
                        .filter(ContainerStatus::getReady).count();
            }
            podMap.put("ready", readyContainers + "/" + totalContainers);

            return podMap;
        }).collect(Collectors.toList());
    }

    @Override
    public void cordonNode(Long clusterId, String nodeName) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.nodes().withName(nodeName).edit(n -> {
            n.getSpec().setUnschedulable(true);
            return n;
        });
    }

    @Override
    public void uncordonNode(Long clusterId, String nodeName) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.nodes().withName(nodeName).edit(n -> {
            n.getSpec().setUnschedulable(false);
            return n;
        });
    }

    @Override
    public void drainNode(Long clusterId, String nodeName) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        // First cordon the node
        cordonNode(clusterId, nodeName);

        // Then evict all pods (except DaemonSet pods)
        List<Pod> pods = client.pods().inAnyNamespace()
                .withField("spec.nodeName", nodeName).list().getItems();

        for (Pod pod : pods) {
            // Skip DaemonSet pods and mirror pods
            if (pod.getMetadata().getOwnerReferences() != null) {
                boolean isDaemonSet = pod.getMetadata().getOwnerReferences().stream()
                        .anyMatch(ref -> "DaemonSet".equals(ref.getKind()));
                if (isDaemonSet) continue;
            }
            // Skip kube-system critical pods
            if ("kube-system".equals(pod.getMetadata().getNamespace())) continue;

            try {
                client.pods().inNamespace(pod.getMetadata().getNamespace())
                        .withName(pod.getMetadata().getName())
                        .evict();
            } catch (Exception e) {
                log.warn("Failed to evict pod {}/{}: {}", pod.getMetadata().getNamespace(),
                        pod.getMetadata().getName(), e.getMessage());
            }
        }
    }

    @Override
    public void updateLabels(Long clusterId, String nodeName, Map<String, String> labels) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.nodes().withName(nodeName).edit(n -> {
            n.getMetadata().setLabels(labels);
            return n;
        });
    }

    @Override
    public void updateTaints(Long clusterId, String nodeName, List<Map<String, String>> taints) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.nodes().withName(nodeName).edit(n -> {
            List<Taint> taintList = taints.stream().map(t -> {
                Taint taint = new Taint();
                taint.setKey(t.get("key"));
                taint.setValue(t.get("value"));
                taint.setEffect(t.get("effect"));
                return taint;
            }).collect(Collectors.toList());
            n.getSpec().setTaints(taintList);
            return n;
        });
    }

    @Override
    public Map<String, Object> getNodeMetrics(Long clusterId, String nodeName) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        Node node = client.nodes().withName(nodeName).get();
        if (node == null) {
            throw new K8sResourceNotFoundException("Node not found: " + nodeName);
        }

        Map<String, Object> metrics = nodeToMap(node);

        // Count pods on this node
        List<Pod> pods = client.pods().inAnyNamespace()
                .withField("spec.nodeName", nodeName).list().getItems();
        metrics.put("podCount", pods.size());
        metrics.put("runningPods", pods.stream().filter(p -> "Running".equals(p.getStatus().getPhase())).count());

        return metrics;
    }

    private Map<String, Object> nodeToMap(Node node) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", node.getMetadata().getName());
        map.put("labels", node.getMetadata().getLabels());
        map.put("createdAt", node.getMetadata().getCreationTimestamp());
        map.put("unschedulable", Boolean.TRUE.equals(node.getSpec().getUnschedulable()));

        // Status
        boolean ready = false;
        if (node.getStatus().getConditions() != null) {
            ready = node.getStatus().getConditions().stream()
                    .anyMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
        }
        map.put("status", ready ? "Ready" : "NotReady");

        // Addresses
        if (node.getStatus().getAddresses() != null) {
            for (NodeAddress addr : node.getStatus().getAddresses()) {
                if ("InternalIP".equals(addr.getType())) {
                    map.put("internalIP", addr.getAddress());
                } else if ("Hostname".equals(addr.getType())) {
                    map.put("hostname", addr.getAddress());
                }
            }
        }

        // Roles
        Map<String, String> labels = node.getMetadata().getLabels();
        List<String> roles = new ArrayList<>();
        if (labels != null) {
            for (String key : labels.keySet()) {
                if (key.startsWith("node-role.kubernetes.io/")) {
                    roles.add(key.substring("node-role.kubernetes.io/".length()));
                }
            }
        }
        map.put("roles", roles.isEmpty() ? List.of("worker") : roles);

        // Capacity & Allocatable
        Map<String, Quantity> capacity = node.getStatus().getCapacity();
        Map<String, Quantity> allocatable = node.getStatus().getAllocatable();
        if (capacity != null) {
            map.put("cpuCapacity", capacity.get("cpu") != null ? capacity.get("cpu").getAmount() : "0");
            map.put("memoryCapacity", capacity.get("memory") != null ? capacity.get("memory").getAmount() : "0");
            map.put("podCapacity", capacity.get("pods") != null ? capacity.get("pods").getAmount() : "0");
        }
        if (allocatable != null) {
            map.put("cpuAllocatable", allocatable.get("cpu") != null ? allocatable.get("cpu").getAmount() : "0");
            map.put("memoryAllocatable", allocatable.get("memory") != null ? allocatable.get("memory").getAmount() : "0");
        }

        // System info
        NodeSystemInfo nodeInfo = node.getStatus().getNodeInfo();
        if (nodeInfo != null) {
            map.put("osImage", nodeInfo.getOsImage());
            map.put("kernelVersion", nodeInfo.getKernelVersion());
            map.put("containerRuntime", nodeInfo.getContainerRuntimeVersion());
            map.put("kubeletVersion", nodeInfo.getKubeletVersion());
            map.put("architecture", nodeInfo.getArchitecture());
        }

        return map;
    }

    private int getRestartCount(Pod pod) {
        if (pod.getStatus().getContainerStatuses() == null) return 0;
        return pod.getStatus().getContainerStatuses().stream()
                .mapToInt(ContainerStatus::getRestartCount)
                .sum();
    }
}
