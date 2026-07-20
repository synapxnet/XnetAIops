package com.synapxnet.aiopsk8sservice.service.impl;

import com.synapxnet.aiopsk8sservice.exception.K8sResourceNotFoundException;
import com.synapxnet.aiopsk8sservice.service.K8sClientFactory;
import com.synapxnet.aiopsk8sservice.service.K8sPodService;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class K8sPodServiceImpl implements K8sPodService {

    private static final Logger log = LoggerFactory.getLogger(K8sPodServiceImpl.class);

    private final K8sClientFactory clientFactory;

    public K8sPodServiceImpl(K8sClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public List<Map<String, Object>> listPods(Long clusterId, String namespace, Map<String, String> labelSelector) {
        KubernetesClient client = clientFactory.getClient(clusterId);

        List<Pod> pods;
        if (namespace == null || namespace.isEmpty()) {
            if (labelSelector != null && !labelSelector.isEmpty()) {
                pods = client.pods().inAnyNamespace().withLabels(labelSelector).list().getItems();
            } else {
                pods = client.pods().inAnyNamespace().list().getItems();
            }
        } else {
            if (labelSelector != null && !labelSelector.isEmpty()) {
                pods = client.pods().inNamespace(namespace).withLabels(labelSelector).list().getItems();
            } else {
                pods = client.pods().inNamespace(namespace).list().getItems();
            }
        }

        return pods.stream().map(this::podToMap).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getPod(Long clusterId, String namespace, String podName) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
        if (pod == null) {
            throw new K8sResourceNotFoundException("Pod not found: " + namespace + "/" + podName);
        }

        Map<String, Object> map = podToMap(pod);

        // Detailed container info
        if (pod.getSpec().getContainers() != null) {
            List<Map<String, Object>> containers = pod.getSpec().getContainers().stream()
                    .map(c -> containerDetailMap(c, pod.getStatus())).collect(Collectors.toList());
            map.put("containers", containers);
        }

        // Init containers
        if (pod.getSpec().getInitContainers() != null && !pod.getSpec().getInitContainers().isEmpty()) {
            List<Map<String, Object>> initContainers = pod.getSpec().getInitContainers().stream()
                    .map(c -> containerDetailMap(c, pod.getStatus())).collect(Collectors.toList());
            map.put("initContainers", initContainers);
        }

        // Conditions
        if (pod.getStatus().getConditions() != null) {
            List<Map<String, Object>> conditions = pod.getStatus().getConditions().stream().map(cond -> {
                Map<String, Object> condMap = new HashMap<>();
                condMap.put("type", cond.getType());
                condMap.put("status", cond.getStatus());
                condMap.put("reason", cond.getReason());
                condMap.put("message", cond.getMessage());
                condMap.put("lastTransitionTime", cond.getLastTransitionTime());
                return condMap;
            }).collect(Collectors.toList());
            map.put("conditions", conditions);
        }

        // Labels, annotations, volumes
        map.put("labels", pod.getMetadata().getLabels());
        map.put("annotations", pod.getMetadata().getAnnotations());
        map.put("nodeName", pod.getSpec().getNodeName());
        map.put("serviceAccount", pod.getSpec().getServiceAccountName());
        map.put("restartPolicy", pod.getSpec().getRestartPolicy());
        map.put("dnsPolicy", pod.getSpec().getDnsPolicy());

        // Volumes
        if (pod.getSpec().getVolumes() != null) {
            List<Map<String, Object>> volumes = pod.getSpec().getVolumes().stream().map(v -> {
                Map<String, Object> volMap = new HashMap<>();
                volMap.put("name", v.getName());
                if (v.getConfigMap() != null) volMap.put("type", "ConfigMap");
                else if (v.getSecret() != null) volMap.put("type", "Secret");
                else if (v.getPersistentVolumeClaim() != null) {
                    volMap.put("type", "PVC");
                    volMap.put("claimName", v.getPersistentVolumeClaim().getClaimName());
                } else if (v.getEmptyDir() != null) volMap.put("type", "EmptyDir");
                else if (v.getHostPath() != null) {
                    volMap.put("type", "HostPath");
                    volMap.put("path", v.getHostPath().getPath());
                } else {
                    volMap.put("type", "Other");
                }
                return volMap;
            }).collect(Collectors.toList());
            map.put("volumes", volumes);
        }

        // Owner references
        if (pod.getMetadata().getOwnerReferences() != null) {
            List<Map<String, String>> owners = pod.getMetadata().getOwnerReferences().stream().map(ref -> {
                Map<String, String> ownerMap = new HashMap<>();
                ownerMap.put("kind", ref.getKind());
                ownerMap.put("name", ref.getName());
                return ownerMap;
            }).collect(Collectors.toList());
            map.put("ownerReferences", owners);
        }

        // YAML
        map.put("yaml", io.fabric8.kubernetes.client.utils.Serialization.asYaml(pod));

        return map;
    }

    @Override
    public void deletePod(Long clusterId, String namespace, String podName) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.pods().inNamespace(namespace).withName(podName).delete();
    }

    @Override
    public String getPodLogs(Long clusterId, String namespace, String podName, String container, Integer tailLines) {
        KubernetesClient client = clientFactory.getClient(clusterId);

        var logOp = client.pods().inNamespace(namespace).withName(podName);

        try {
            var logBuilder = container != null && !container.isEmpty()
                    ? logOp.inContainer(container)
                    : logOp;

            String logs;
            if (tailLines != null && tailLines > 0) {
                logs = logBuilder.tailingLines(tailLines).getLog();
            } else {
                logs = logBuilder.tailingLines(1000).getLog();
            }
            return logs;
        } catch (Exception e) {
            log.error("Failed to get pod logs {}/{}: {}", namespace, podName, e.getMessage());
            return "Error fetching logs: " + e.getMessage();
        }
    }

    @Override
    public List<Map<String, Object>> getPodEvents(Long clusterId, String namespace, String podName) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        List<Event> events = client.v1().events().inNamespace(namespace).list().getItems().stream()
                .filter(e -> e.getInvolvedObject() != null
                        && "Pod".equals(e.getInvolvedObject().getKind())
                        && podName.equals(e.getInvolvedObject().getName()))
                .sorted((a, b) -> {
                    String ta = a.getLastTimestamp() != null ? a.getLastTimestamp() : a.getMetadata().getCreationTimestamp();
                    String tb = b.getLastTimestamp() != null ? b.getLastTimestamp() : b.getMetadata().getCreationTimestamp();
                    if (ta == null || tb == null) return 0;
                    return tb.compareTo(ta);
                })
                .collect(Collectors.toList());

        return events.stream().map(event -> {
            Map<String, Object> map = new HashMap<>();
            map.put("type", event.getType());
            map.put("reason", event.getReason());
            map.put("message", event.getMessage());
            map.put("count", event.getCount());
            map.put("lastTimestamp", event.getLastTimestamp());
            map.put("firstTimestamp", event.getMetadata().getCreationTimestamp());
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getPodContainers(Long clusterId, String namespace, String podName) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
        if (pod == null) {
            throw new K8sResourceNotFoundException("Pod not found: " + namespace + "/" + podName);
        }

        List<Map<String, Object>> result = new ArrayList<>();

        if (pod.getSpec().getContainers() != null) {
            for (Container c : pod.getSpec().getContainers()) {
                Map<String, Object> containerMap = containerDetailMap(c, pod.getStatus());
                containerMap.put("isInit", false);
                result.add(containerMap);
            }
        }
        if (pod.getSpec().getInitContainers() != null) {
            for (Container c : pod.getSpec().getInitContainers()) {
                Map<String, Object> containerMap = containerDetailMap(c, pod.getStatus());
                containerMap.put("isInit", true);
                result.add(containerMap);
            }
        }

        return result;
    }

    // ====== Helpers ======

    private Map<String, Object> podToMap(Pod pod) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", pod.getMetadata().getName());
        map.put("namespace", pod.getMetadata().getNamespace());
        map.put("status", pod.getStatus().getPhase());
        map.put("createdAt", pod.getMetadata().getCreationTimestamp());
        map.put("podIP", pod.getStatus().getPodIP());
        map.put("hostIP", pod.getStatus().getHostIP());
        map.put("nodeName", pod.getSpec().getNodeName());

        // Restart count
        int restarts = 0;
        if (pod.getStatus().getContainerStatuses() != null) {
            restarts = pod.getStatus().getContainerStatuses().stream()
                    .mapToInt(ContainerStatus::getRestartCount).sum();
        }
        map.put("restarts", restarts);

        // Ready containers
        int readyContainers = 0;
        int totalContainers = pod.getSpec().getContainers() != null ? pod.getSpec().getContainers().size() : 0;
        if (pod.getStatus().getContainerStatuses() != null) {
            readyContainers = (int) pod.getStatus().getContainerStatuses().stream()
                    .filter(ContainerStatus::getReady).count();
        }
        map.put("ready", readyContainers + "/" + totalContainers);
        map.put("readyCount", readyContainers);
        map.put("totalContainers", totalContainers);

        // Images
        if (pod.getSpec().getContainers() != null) {
            List<String> images = pod.getSpec().getContainers().stream()
                    .map(Container::getImage).collect(Collectors.toList());
            map.put("images", images);
        }

        return map;
    }

    private Map<String, Object> containerDetailMap(Container container, PodStatus podStatus) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", container.getName());
        map.put("image", container.getImage());
        map.put("imagePullPolicy", container.getImagePullPolicy());

        // Find matching container status
        if (podStatus != null && podStatus.getContainerStatuses() != null) {
            for (ContainerStatus cs : podStatus.getContainerStatuses()) {
                if (cs.getName().equals(container.getName())) {
                    map.put("ready", cs.getReady());
                    map.put("restartCount", cs.getRestartCount());
                    map.put("started", cs.getStarted());
                    map.put("containerID", cs.getContainerID());

                    // State
                    if (cs.getState() != null) {
                        if (cs.getState().getRunning() != null) {
                            map.put("state", "Running");
                            map.put("startedAt", cs.getState().getRunning().getStartedAt());
                        } else if (cs.getState().getWaiting() != null) {
                            map.put("state", "Waiting");
                            map.put("waitingReason", cs.getState().getWaiting().getReason());
                            map.put("waitingMessage", cs.getState().getWaiting().getMessage());
                        } else if (cs.getState().getTerminated() != null) {
                            map.put("state", "Terminated");
                            map.put("terminatedReason", cs.getState().getTerminated().getReason());
                            map.put("exitCode", cs.getState().getTerminated().getExitCode());
                        }
                    }
                    break;
                }
            }
        }

        // Ports
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

        // Resources
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

        // Volume mounts
        if (container.getVolumeMounts() != null) {
            List<Map<String, Object>> mounts = container.getVolumeMounts().stream().map(vm -> {
                Map<String, Object> mountMap = new HashMap<>();
                mountMap.put("name", vm.getName());
                mountMap.put("mountPath", vm.getMountPath());
                mountMap.put("readOnly", vm.getReadOnly());
                mountMap.put("subPath", vm.getSubPath());
                return mountMap;
            }).collect(Collectors.toList());
            map.put("volumeMounts", mounts);
        }

        // Env vars
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
}
