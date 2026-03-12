package com.synapxnet.aiopsk8sservice.service.impl;

import com.synapxnet.aiopsk8sservice.exception.K8sResourceNotFoundException;
import com.synapxnet.aiopsk8sservice.service.K8sClientFactory;
import com.synapxnet.aiopsk8sservice.service.K8sNamespaceService;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class K8sNamespaceServiceImpl implements K8sNamespaceService {

    private static final Logger log = LoggerFactory.getLogger(K8sNamespaceServiceImpl.class);

    private final K8sClientFactory clientFactory;

    public K8sNamespaceServiceImpl(K8sClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public List<Map<String, Object>> listNamespaces(Long clusterId) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        List<Namespace> namespaces = client.namespaces().list().getItems();
        return namespaces.stream().map(ns -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", ns.getMetadata().getName());
            map.put("status", ns.getStatus() != null ? ns.getStatus().getPhase() : "Unknown");
            map.put("labels", ns.getMetadata().getLabels());
            map.put("annotations", ns.getMetadata().getAnnotations());
            map.put("createdAt", ns.getMetadata().getCreationTimestamp());

            // Count resources in namespace
            try {
                int podCount = client.pods().inNamespace(ns.getMetadata().getName()).list().getItems().size();
                int deploymentCount = client.apps().deployments().inNamespace(ns.getMetadata().getName()).list().getItems().size();
                int serviceCount = client.services().inNamespace(ns.getMetadata().getName()).list().getItems().size();
                map.put("podCount", podCount);
                map.put("deploymentCount", deploymentCount);
                map.put("serviceCount", serviceCount);
            } catch (Exception e) {
                log.debug("Failed to count resources for namespace {}: {}", ns.getMetadata().getName(), e.getMessage());
                map.put("podCount", 0);
                map.put("deploymentCount", 0);
                map.put("serviceCount", 0);
            }

            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getNamespace(Long clusterId, String namespace) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        Namespace ns = client.namespaces().withName(namespace).get();
        if (ns == null) {
            throw new K8sResourceNotFoundException("Namespace not found: " + namespace);
        }
        Map<String, Object> map = new HashMap<>();
        map.put("name", ns.getMetadata().getName());
        map.put("status", ns.getStatus() != null ? ns.getStatus().getPhase() : "Unknown");
        map.put("labels", ns.getMetadata().getLabels());
        map.put("annotations", ns.getMetadata().getAnnotations());
        map.put("createdAt", ns.getMetadata().getCreationTimestamp());
        return map;
    }

    @Override
    public void createNamespace(Long clusterId, String name, Map<String, String> labels) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        Namespace ns = new NamespaceBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withLabels(labels)
                .endMetadata()
                .build();
        client.namespaces().resource(ns).create();
    }

    @Override
    public void deleteNamespace(Long clusterId, String namespace) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.namespaces().withName(namespace).delete();
    }

    @Override
    public Map<String, Object> getNamespaceOverview(Long clusterId, String namespace) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        Map<String, Object> overview = new HashMap<>();

        int podCount = client.pods().inNamespace(namespace).list().getItems().size();
        int runningPods = (int) client.pods().inNamespace(namespace).list().getItems().stream()
                .filter(p -> "Running".equals(p.getStatus().getPhase())).count();
        int deploymentCount = client.apps().deployments().inNamespace(namespace).list().getItems().size();
        int statefulSetCount = client.apps().statefulSets().inNamespace(namespace).list().getItems().size();
        int daemonSetCount = client.apps().daemonSets().inNamespace(namespace).list().getItems().size();
        int serviceCount = client.services().inNamespace(namespace).list().getItems().size();
        int jobCount = client.batch().v1().jobs().inNamespace(namespace).list().getItems().size();
        int cronJobCount = client.batch().v1().cronjobs().inNamespace(namespace).list().getItems().size();
        int configMapCount = client.configMaps().inNamespace(namespace).list().getItems().size();
        int secretCount = client.secrets().inNamespace(namespace).list().getItems().size();
        int ingressCount = client.network().v1().ingresses().inNamespace(namespace).list().getItems().size();

        overview.put("podCount", podCount);
        overview.put("runningPods", runningPods);
        overview.put("deploymentCount", deploymentCount);
        overview.put("statefulSetCount", statefulSetCount);
        overview.put("daemonSetCount", daemonSetCount);
        overview.put("serviceCount", serviceCount);
        overview.put("jobCount", jobCount);
        overview.put("cronJobCount", cronJobCount);
        overview.put("configMapCount", configMapCount);
        overview.put("secretCount", secretCount);
        overview.put("ingressCount", ingressCount);

        return overview;
    }

    @Override
    public List<Map<String, Object>> getNamespaceEvents(Long clusterId, String namespace, int limit) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        List<Event> events = client.v1().events().inNamespace(namespace).list().getItems();

        events.sort((a, b) -> {
            String ta = a.getLastTimestamp() != null ? a.getLastTimestamp() : a.getMetadata().getCreationTimestamp();
            String tb = b.getLastTimestamp() != null ? b.getLastTimestamp() : b.getMetadata().getCreationTimestamp();
            if (ta == null || tb == null) return 0;
            return tb.compareTo(ta);
        });

        return events.stream().limit(limit).map(event -> {
            Map<String, Object> map = new HashMap<>();
            map.put("type", event.getType());
            map.put("reason", event.getReason());
            map.put("message", event.getMessage());
            map.put("count", event.getCount());
            map.put("lastTimestamp", event.getLastTimestamp());
            if (event.getInvolvedObject() != null) {
                map.put("kind", event.getInvolvedObject().getKind());
                map.put("name", event.getInvolvedObject().getName());
                map.put("namespace", event.getInvolvedObject().getNamespace());
            }
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getResourceQuotas(Long clusterId, String namespace) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        List<ResourceQuota> quotas = client.resourceQuotas().inNamespace(namespace).list().getItems();
        return quotas.stream().map(rq -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", rq.getMetadata().getName());
            map.put("createdAt", rq.getMetadata().getCreationTimestamp());
            if (rq.getSpec() != null && rq.getSpec().getHard() != null) {
                Map<String, String> hard = new HashMap<>();
                rq.getSpec().getHard().forEach((k, v) -> hard.put(k, v.getAmount()));
                map.put("hard", hard);
            }
            if (rq.getStatus() != null && rq.getStatus().getUsed() != null) {
                Map<String, String> used = new HashMap<>();
                rq.getStatus().getUsed().forEach((k, v) -> used.put(k, v.getAmount()));
                map.put("used", used);
            }
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public void setResourceQuota(Long clusterId, String namespace, Map<String, String> hard) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        Map<String, Quantity> hardQuantities = new HashMap<>();
        hard.forEach((k, v) -> hardQuantities.put(k, new Quantity(v)));

        ResourceQuota quota = new ResourceQuotaBuilder()
                .withNewMetadata()
                    .withName(namespace + "-quota")
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .withHard(hardQuantities)
                .endSpec()
                .build();
        client.resourceQuotas().inNamespace(namespace).resource(quota).serverSideApply();
    }
}
