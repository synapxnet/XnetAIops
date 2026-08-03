package com.synapxnet.aiopsk8sservice.service.impl;

import com.synapxnet.aiopsk8sservice.exception.K8sResourceNotFoundException;
import com.synapxnet.aiopsk8sservice.service.K8sClientFactory;
import com.synapxnet.aiopsk8sservice.service.K8sWorkloadService;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class K8sWorkloadServiceImpl implements K8sWorkloadService {

    private static final Logger log = LoggerFactory.getLogger(K8sWorkloadServiceImpl.class);

    private final K8sClientFactory clientFactory;

    public K8sWorkloadServiceImpl(K8sClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    // ====== Deployments ======

    @Override
    public List<Map<String, Object>> listDeployments(Long clusterId, String namespace) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        List<Deployment> deployments;
        if (namespace == null || namespace.isEmpty()) {
            deployments = client.apps().deployments().inAnyNamespace().list().getItems();
        } else {
            deployments = client.apps().deployments().inNamespace(namespace).list().getItems();
        }
        return deployments.stream().map(this::deploymentToMap).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getDeployment(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        Deployment deploy = client.apps().deployments().inNamespace(namespace).withName(name).get();
        if (deploy == null) {
            throw new K8sResourceNotFoundException("Deployment not found: " + namespace + "/" + name);
        }
        Map<String, Object> map = deploymentToMap(deploy);

        // Add containers info
        if (deploy.getSpec().getTemplate().getSpec().getContainers() != null) {
            List<Map<String, Object>> containers = deploy.getSpec().getTemplate().getSpec().getContainers().stream()
                    .map(this::containerToMap).collect(Collectors.toList());
            map.put("containers", containers);
        }

        // Add labels & selectors
        map.put("labels", deploy.getMetadata().getLabels());
        map.put("annotations", deploy.getMetadata().getAnnotations());
        map.put("resourceVersion", deploy.getMetadata().getResourceVersion());
        map.put("currentRevision", deploy.getMetadata().getAnnotations() == null ? null
                : deploy.getMetadata().getAnnotations().get("deployment.kubernetes.io/revision"));
        if (deploy.getSpec().getSelector() != null) {
            map.put("selector", deploy.getSpec().getSelector().getMatchLabels());
        }

        // Strategy
        if (deploy.getSpec().getStrategy() != null) {
            map.put("strategy", deploy.getSpec().getStrategy().getType());
        }
        if (deploy.getStatus() != null && deploy.getStatus().getConditions() != null) {
            map.put("conditions", deploy.getStatus().getConditions().stream().map(condition -> Map.of(
                    "type", valueOrEmpty(condition.getType()),
                    "status", valueOrEmpty(condition.getStatus()),
                    "reason", valueOrEmpty(condition.getReason()),
                    "message", valueOrEmpty(condition.getMessage()),
                    "lastTransitionTime", valueOrEmpty(condition.getLastTransitionTime())
            )).toList());
        }

        // YAML representation
        map.put("yaml", Serialization.asYaml(deploy));

        return map;
    }

    @Override
    public void createDeployment(Long clusterId, String namespace, String yaml) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        Deployment deploy = Serialization.unmarshal(yaml, Deployment.class);
        if (deploy.getMetadata().getNamespace() == null) {
            deploy.getMetadata().setNamespace(namespace);
        }
        client.apps().deployments().inNamespace(namespace).resource(deploy).create();
    }

    @Override
    public void updateDeployment(Long clusterId, String namespace, String name, String yaml) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        Deployment deploy = Serialization.unmarshal(yaml, Deployment.class);
        deploy.getMetadata().setName(name);
        deploy.getMetadata().setNamespace(namespace);
        client.apps().deployments().inNamespace(namespace).resource(deploy).update();
    }

    @Override
    public void deleteDeployment(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.apps().deployments().inNamespace(namespace).withName(name).delete();
    }

    @Override
    public void scaleDeployment(Long clusterId, String namespace, String name, int replicas) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.apps().deployments().inNamespace(namespace).withName(name).scale(replicas);
    }

    @Override
    public void restartDeployment(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.apps().deployments().inNamespace(namespace).withName(name).rolling().restart();
    }

    @Override
    public List<Map<String, Object>> getDeploymentRevisions(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        Deployment deploy = client.apps().deployments().inNamespace(namespace).withName(name).get();
        if (deploy == null) {
            throw new K8sResourceNotFoundException("Deployment not found: " + namespace + "/" + name);
        }

        // Get ReplicaSets for this deployment
        Map<String, String> selector = deploy.getSpec().getSelector().getMatchLabels();
        List<ReplicaSet> replicaSets = client.apps().replicaSets().inNamespace(namespace).list().getItems().stream()
                .filter(rs -> {
                    if (rs.getMetadata().getOwnerReferences() == null) return false;
                    return rs.getMetadata().getOwnerReferences().stream()
                            .anyMatch(ref -> "Deployment".equals(ref.getKind()) && name.equals(ref.getName()));
                })
                .sorted((a, b) -> {
                    String revA = a.getMetadata().getAnnotations() != null
                            ? a.getMetadata().getAnnotations().getOrDefault("deployment.kubernetes.io/revision", "0") : "0";
                    String revB = b.getMetadata().getAnnotations() != null
                            ? b.getMetadata().getAnnotations().getOrDefault("deployment.kubernetes.io/revision", "0") : "0";
                    return Long.compare(Long.parseLong(revB), Long.parseLong(revA));
                })
                .collect(Collectors.toList());

        return replicaSets.stream().map(rs -> {
            Map<String, Object> map = new HashMap<>();
            String revision = rs.getMetadata().getAnnotations() != null
                    ? rs.getMetadata().getAnnotations().get("deployment.kubernetes.io/revision") : "0";
            map.put("revision", Long.parseLong(revision != null ? revision : "0"));
            map.put("name", rs.getMetadata().getName());
            map.put("replicas", rs.getSpec().getReplicas());
            map.put("readyReplicas", rs.getStatus().getReadyReplicas() != null ? rs.getStatus().getReadyReplicas() : 0);
            map.put("createdAt", rs.getMetadata().getCreationTimestamp());

            // Images in this revision
            if (rs.getSpec().getTemplate().getSpec().getContainers() != null) {
                List<String> images = rs.getSpec().getTemplate().getSpec().getContainers().stream()
                        .map(Container::getImage).collect(Collectors.toList());
                map.put("images", images);
            }
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public void rollbackDeployment(Long clusterId, String namespace, String name, Long revision) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        // Find the ReplicaSet with the target revision
        List<ReplicaSet> replicaSets = client.apps().replicaSets().inNamespace(namespace).list().getItems().stream()
                .filter(rs -> {
                    if (rs.getMetadata().getOwnerReferences() == null) return false;
                    return rs.getMetadata().getOwnerReferences().stream()
                            .anyMatch(ref -> "Deployment".equals(ref.getKind()) && name.equals(ref.getName()));
                })
                .filter(rs -> {
                    String rev = rs.getMetadata().getAnnotations() != null
                            ? rs.getMetadata().getAnnotations().get("deployment.kubernetes.io/revision") : null;
                    return rev != null && Long.parseLong(rev) == revision;
                })
                .collect(Collectors.toList());

        if (replicaSets.isEmpty()) {
            throw new K8sResourceNotFoundException("Revision " + revision + " not found for deployment " + name);
        }

        ReplicaSet targetRS = replicaSets.get(0);
        // Update deployment with the target RS's template
        client.apps().deployments().inNamespace(namespace).withName(name).edit(deploy -> {
            deploy.getSpec().setTemplate(targetRS.getSpec().getTemplate());
            // Add annotation to record the rollback
            if (deploy.getMetadata().getAnnotations() == null) {
                deploy.getMetadata().setAnnotations(new HashMap<>());
            }
            deploy.getMetadata().getAnnotations().put("kubectl.kubernetes.io/restartedAt", Instant.now().toString());
            return deploy;
        });
    }

    // ====== StatefulSets ======

    @Override
    public List<Map<String, Object>> listStatefulSets(Long clusterId, String namespace) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        List<StatefulSet> statefulSets;
        if (namespace == null || namespace.isEmpty()) {
            statefulSets = client.apps().statefulSets().inAnyNamespace().list().getItems();
        } else {
            statefulSets = client.apps().statefulSets().inNamespace(namespace).list().getItems();
        }
        return statefulSets.stream().map(this::statefulSetToMap).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getStatefulSet(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        StatefulSet sts = client.apps().statefulSets().inNamespace(namespace).withName(name).get();
        if (sts == null) {
            throw new K8sResourceNotFoundException("StatefulSet not found: " + namespace + "/" + name);
        }
        Map<String, Object> map = statefulSetToMap(sts);

        if (sts.getSpec().getTemplate().getSpec().getContainers() != null) {
            List<Map<String, Object>> containers = sts.getSpec().getTemplate().getSpec().getContainers().stream()
                    .map(this::containerToMap).collect(Collectors.toList());
            map.put("containers", containers);
        }
        map.put("labels", sts.getMetadata().getLabels());
        map.put("annotations", sts.getMetadata().getAnnotations());
        map.put("resourceVersion", sts.getMetadata().getResourceVersion());
        map.put("currentRevision", sts.getStatus() == null ? null : sts.getStatus().getCurrentRevision());
        if (sts.getSpec().getSelector() != null) {
            map.put("selector", sts.getSpec().getSelector().getMatchLabels());
        }
        if (sts.getStatus() != null && sts.getStatus().getConditions() != null) {
            map.put("conditions", sts.getStatus().getConditions().stream().map(condition -> Map.of(
                    "type", valueOrEmpty(condition.getType()),
                    "status", valueOrEmpty(condition.getStatus()),
                    "reason", valueOrEmpty(condition.getReason()),
                    "message", valueOrEmpty(condition.getMessage()),
                    "lastTransitionTime", valueOrEmpty(condition.getLastTransitionTime())
            )).toList());
        }
        map.put("yaml", Serialization.asYaml(sts));

        return map;
    }

    @Override
    public void createStatefulSet(Long clusterId, String namespace, String yaml) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        StatefulSet sts = Serialization.unmarshal(yaml, StatefulSet.class);
        if (sts.getMetadata().getNamespace() == null) {
            sts.getMetadata().setNamespace(namespace);
        }
        client.apps().statefulSets().inNamespace(namespace).resource(sts).create();
    }

    @Override
    public void updateStatefulSet(Long clusterId, String namespace, String name, String yaml) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        StatefulSet sts = Serialization.unmarshal(yaml, StatefulSet.class);
        sts.getMetadata().setName(name);
        sts.getMetadata().setNamespace(namespace);
        client.apps().statefulSets().inNamespace(namespace).resource(sts).update();
    }

    @Override
    public void deleteStatefulSet(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.apps().statefulSets().inNamespace(namespace).withName(name).delete();
    }

    @Override
    public void scaleStatefulSet(Long clusterId, String namespace, String name, int replicas) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.apps().statefulSets().inNamespace(namespace).withName(name).scale(replicas);
    }

    @Override
    public void restartStatefulSet(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.apps().statefulSets().inNamespace(namespace).withName(name).rolling().restart();
    }

    // ====== DaemonSets ======

    @Override
    public List<Map<String, Object>> listDaemonSets(Long clusterId, String namespace) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        List<DaemonSet> daemonSets;
        if (namespace == null || namespace.isEmpty()) {
            daemonSets = client.apps().daemonSets().inAnyNamespace().list().getItems();
        } else {
            daemonSets = client.apps().daemonSets().inNamespace(namespace).list().getItems();
        }
        return daemonSets.stream().map(this::daemonSetToMap).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getDaemonSet(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        DaemonSet ds = client.apps().daemonSets().inNamespace(namespace).withName(name).get();
        if (ds == null) {
            throw new K8sResourceNotFoundException("DaemonSet not found: " + namespace + "/" + name);
        }
        Map<String, Object> map = daemonSetToMap(ds);

        if (ds.getSpec().getTemplate().getSpec().getContainers() != null) {
            List<Map<String, Object>> containers = ds.getSpec().getTemplate().getSpec().getContainers().stream()
                    .map(this::containerToMap).collect(Collectors.toList());
            map.put("containers", containers);
        }
        map.put("labels", ds.getMetadata().getLabels());
        map.put("annotations", ds.getMetadata().getAnnotations());
        map.put("resourceVersion", ds.getMetadata().getResourceVersion());
        map.put("currentRevision", ds.getMetadata().getGeneration());
        if (ds.getSpec().getSelector() != null) {
            map.put("selector", ds.getSpec().getSelector().getMatchLabels());
        }
        if (ds.getStatus() != null && ds.getStatus().getConditions() != null) {
            map.put("conditions", ds.getStatus().getConditions().stream().map(condition -> Map.of(
                    "type", valueOrEmpty(condition.getType()),
                    "status", valueOrEmpty(condition.getStatus()),
                    "reason", valueOrEmpty(condition.getReason()),
                    "message", valueOrEmpty(condition.getMessage()),
                    "lastTransitionTime", valueOrEmpty(condition.getLastTransitionTime())
            )).toList());
        }
        map.put("yaml", Serialization.asYaml(ds));

        return map;
    }

    @Override
    public void createDaemonSet(Long clusterId, String namespace, String yaml) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        DaemonSet ds = Serialization.unmarshal(yaml, DaemonSet.class);
        if (ds.getMetadata().getNamespace() == null) {
            ds.getMetadata().setNamespace(namespace);
        }
        client.apps().daemonSets().inNamespace(namespace).resource(ds).create();
    }

    @Override
    public void updateDaemonSet(Long clusterId, String namespace, String name, String yaml) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        DaemonSet ds = Serialization.unmarshal(yaml, DaemonSet.class);
        ds.getMetadata().setName(name);
        ds.getMetadata().setNamespace(namespace);
        client.apps().daemonSets().inNamespace(namespace).resource(ds).update();
    }

    @Override
    public void deleteDaemonSet(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.apps().daemonSets().inNamespace(namespace).withName(name).delete();
    }

    @Override
    public void restartDaemonSet(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        DaemonSet ds = client.apps().daemonSets().inNamespace(namespace).withName(name).get();
        if (ds == null) {
            throw new K8sResourceNotFoundException("DaemonSet not found: " + namespace + "/" + name);
        }
        // Trigger rolling restart by updating pod template annotation
        if (ds.getSpec().getTemplate().getMetadata().getAnnotations() == null) {
            ds.getSpec().getTemplate().getMetadata().setAnnotations(new java.util.HashMap<>());
        }
        ds.getSpec().getTemplate().getMetadata().getAnnotations()
                .put("kubectl.kubernetes.io/restartedAt", java.time.Instant.now().toString());
        client.apps().daemonSets().inNamespace(namespace).resource(ds).update();
    }

    // ====== Helper methods ======

    private Map<String, Object> deploymentToMap(Deployment deploy) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", deploy.getMetadata().getName());
        map.put("namespace", deploy.getMetadata().getNamespace());
        map.put("createdAt", deploy.getMetadata().getCreationTimestamp());
        map.put("kind", "Deployment");

        DeploymentStatus status = deploy.getStatus();
        int replicas = deploy.getSpec().getReplicas() != null ? deploy.getSpec().getReplicas() : 0;
        int ready = status != null && status.getReadyReplicas() != null ? status.getReadyReplicas() : 0;
        int updated = status != null && status.getUpdatedReplicas() != null ? status.getUpdatedReplicas() : 0;
        int available = status != null && status.getAvailableReplicas() != null ? status.getAvailableReplicas() : 0;

        map.put("replicas", replicas);
        map.put("readyReplicas", ready);
        map.put("updatedReplicas", updated);
        map.put("availableReplicas", available);
        map.put("status", ready >= replicas && replicas > 0 ? "Running" : "Updating");

        // Images
        if (deploy.getSpec().getTemplate().getSpec().getContainers() != null) {
            List<String> images = deploy.getSpec().getTemplate().getSpec().getContainers().stream()
                    .map(Container::getImage).collect(Collectors.toList());
            map.put("images", images);
        }

        return map;
    }

    private Map<String, Object> statefulSetToMap(StatefulSet sts) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", sts.getMetadata().getName());
        map.put("namespace", sts.getMetadata().getNamespace());
        map.put("createdAt", sts.getMetadata().getCreationTimestamp());
        map.put("kind", "StatefulSet");

        StatefulSetStatus status = sts.getStatus();
        int replicas = sts.getSpec().getReplicas() != null ? sts.getSpec().getReplicas() : 0;
        int ready = status != null && status.getReadyReplicas() != null ? status.getReadyReplicas() : 0;
        int updated = status != null && status.getUpdatedReplicas() != null ? status.getUpdatedReplicas() : 0;

        map.put("replicas", replicas);
        map.put("readyReplicas", ready);
        map.put("updatedReplicas", updated);
        map.put("status", ready >= replicas && replicas > 0 ? "Running" : "Updating");

        if (sts.getSpec().getTemplate().getSpec().getContainers() != null) {
            List<String> images = sts.getSpec().getTemplate().getSpec().getContainers().stream()
                    .map(Container::getImage).collect(Collectors.toList());
            map.put("images", images);
        }

        return map;
    }

    private Map<String, Object> daemonSetToMap(DaemonSet ds) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", ds.getMetadata().getName());
        map.put("namespace", ds.getMetadata().getNamespace());
        map.put("createdAt", ds.getMetadata().getCreationTimestamp());
        map.put("kind", "DaemonSet");

        DaemonSetStatus status = ds.getStatus();
        int desired = status != null && status.getDesiredNumberScheduled() != null ? status.getDesiredNumberScheduled() : 0;
        int ready = status != null && status.getNumberReady() != null ? status.getNumberReady() : 0;
        int available = status != null && status.getNumberAvailable() != null ? status.getNumberAvailable() : 0;
        int updated = status != null && status.getUpdatedNumberScheduled() != null ? status.getUpdatedNumberScheduled() : 0;

        map.put("desiredNumberScheduled", desired);
        map.put("numberReady", ready);
        map.put("numberAvailable", available);
        map.put("updatedNumberScheduled", updated);
        map.put("replicas", desired);
        map.put("readyReplicas", ready);
        map.put("status", ready >= desired && desired > 0 ? "Running" : "Updating");

        if (ds.getSpec().getTemplate().getSpec().getContainers() != null) {
            List<String> images = ds.getSpec().getTemplate().getSpec().getContainers().stream()
                    .map(Container::getImage).collect(Collectors.toList());
            map.put("images", images);
        }

        return map;
    }

    private Map<String, Object> containerToMap(Container container) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", container.getName());
        map.put("image", container.getImage());
        map.put("imagePullPolicy", container.getImagePullPolicy());

        if (container.getPorts() != null) {
            List<Map<String, Object>> ports = container.getPorts().stream().map(p -> {
                Map<String, Object> portMap = new HashMap<>();
                portMap.put("name", p.getName());
                portMap.put("containerPort", p.getContainerPort());
                portMap.put("protocol", p.getProtocol());
                return portMap;
            }).collect(Collectors.toList());
            map.put("ports", ports);
        }

        if (container.getResources() != null) {
            Map<String, Object> resources = new HashMap<>();
            if (container.getResources().getRequests() != null) {
                Map<String, String> requests = new HashMap<>();
                container.getResources().getRequests().forEach((k, v) -> requests.put(k, v.getAmount()));
                resources.put("requests", requests);
            }
            if (container.getResources().getLimits() != null) {
                Map<String, String> limits = new HashMap<>();
                container.getResources().getLimits().forEach((k, v) -> limits.put(k, v.getAmount()));
                resources.put("limits", limits);
            }
            map.put("resources", resources);
        }

        if (container.getEnv() != null) {
            List<Map<String, String>> envVars = container.getEnv().stream().map(e -> {
                Map<String, String> envMap = new HashMap<>();
                envMap.put("name", e.getName());
                envMap.put("value", e.getValue());
                return envMap;
            }).collect(Collectors.toList());
            map.put("env", envVars);
        }

        return map;
    }

    /**
     * 将可能为空的 Kubernetes 条件字段转换为空字符串，供不可变 Map 安全承载。
     *
     * @param value Kubernetes 条件字段
     * @return 非 null 字符串
     */
    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
