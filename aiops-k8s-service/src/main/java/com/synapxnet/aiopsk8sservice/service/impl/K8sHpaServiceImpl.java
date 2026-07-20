package com.synapxnet.aiopsk8sservice.service.impl;

import com.synapxnet.aiopsk8sservice.service.K8sClientFactory;
import com.synapxnet.aiopsk8sservice.service.K8sHpaService;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class K8sHpaServiceImpl implements K8sHpaService {

    private final K8sClientFactory clientFactory;

    public K8sHpaServiceImpl(K8sClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public List<Map<String, Object>> listHpas(Long clusterId, String namespace) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        return client.autoscaling().v2().horizontalPodAutoscalers()
                .inNamespace(namespace).list().getItems().stream()
                .map(this::mapHpa)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getHpa(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        HorizontalPodAutoscaler hpa = client.autoscaling().v2().horizontalPodAutoscalers()
                .inNamespace(namespace).withName(name).get();
        if (hpa == null) return null;
        Map<String, Object> result = mapHpa(hpa);
        result.put("yaml", Serialization.asYaml(hpa));
        // Detailed metrics
        if (hpa.getSpec().getMetrics() != null) {
            result.put("metrics", hpa.getSpec().getMetrics().stream().map(m -> {
                Map<String, Object> metric = new HashMap<>();
                metric.put("type", m.getType());
                if (m.getResource() != null) {
                    metric.put("resourceName", m.getResource().getName());
                    if (m.getResource().getTarget() != null) {
                        metric.put("targetType", m.getResource().getTarget().getType());
                        metric.put("targetAverageUtilization", m.getResource().getTarget().getAverageUtilization());
                        metric.put("targetAverageValue", m.getResource().getTarget().getAverageValue());
                    }
                }
                return metric;
            }).collect(Collectors.toList()));
        }
        // Conditions
        if (hpa.getStatus() != null && hpa.getStatus().getConditions() != null) {
            result.put("conditions", hpa.getStatus().getConditions().stream().map(c -> {
                Map<String, Object> cond = new HashMap<>();
                cond.put("type", c.getType());
                cond.put("status", c.getStatus());
                cond.put("reason", c.getReason());
                cond.put("message", c.getMessage());
                cond.put("lastTransitionTime", c.getLastTransitionTime());
                return cond;
            }).collect(Collectors.toList()));
        }
        return result;
    }

    @Override
    public void createHpaFromYaml(Long clusterId, String namespace, String yaml) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        HorizontalPodAutoscaler hpa = Serialization.unmarshal(yaml, HorizontalPodAutoscaler.class);
        hpa.getMetadata().setNamespace(namespace);
        client.autoscaling().v2().horizontalPodAutoscalers().inNamespace(namespace).resource(hpa).create();
    }

    @Override
    public void updateHpaFromYaml(Long clusterId, String namespace, String name, String yaml) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        HorizontalPodAutoscaler hpa = Serialization.unmarshal(yaml, HorizontalPodAutoscaler.class);
        hpa.getMetadata().setNamespace(namespace);
        hpa.getMetadata().setName(name);
        client.autoscaling().v2().horizontalPodAutoscalers().inNamespace(namespace).resource(hpa).update();
    }

    @Override
    public void deleteHpa(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.autoscaling().v2().horizontalPodAutoscalers().inNamespace(namespace).withName(name).delete();
    }

    private Map<String, Object> mapHpa(HorizontalPodAutoscaler hpa) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", hpa.getMetadata().getName());
        map.put("namespace", hpa.getMetadata().getNamespace());
        map.put("labels", hpa.getMetadata().getLabels());
        map.put("createdAt", hpa.getMetadata().getCreationTimestamp());

        if (hpa.getSpec() != null) {
            map.put("minReplicas", hpa.getSpec().getMinReplicas());
            map.put("maxReplicas", hpa.getSpec().getMaxReplicas());
            if (hpa.getSpec().getScaleTargetRef() != null) {
                map.put("targetKind", hpa.getSpec().getScaleTargetRef().getKind());
                map.put("targetName", hpa.getSpec().getScaleTargetRef().getName());
            }
            map.put("metricCount", hpa.getSpec().getMetrics() != null ? hpa.getSpec().getMetrics().size() : 0);
        }

        if (hpa.getStatus() != null) {
            map.put("currentReplicas", hpa.getStatus().getCurrentReplicas());
            map.put("desiredReplicas", hpa.getStatus().getDesiredReplicas());
        }
        return map;
    }
}
