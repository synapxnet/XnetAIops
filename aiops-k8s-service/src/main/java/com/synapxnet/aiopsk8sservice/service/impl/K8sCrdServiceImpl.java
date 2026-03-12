package com.synapxnet.aiopsk8sservice.service.impl;

import com.synapxnet.aiopsk8sservice.service.K8sClientFactory;
import com.synapxnet.aiopsk8sservice.service.K8sCrdService;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class K8sCrdServiceImpl implements K8sCrdService {

    private final K8sClientFactory clientFactory;

    public K8sCrdServiceImpl(K8sClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public List<Map<String, Object>> listCrds(Long clusterId) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        return client.apiextensions().v1().customResourceDefinitions().list().getItems().stream()
                .map(this::mapCrd)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getCrd(Long clusterId, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        CustomResourceDefinition crd = client.apiextensions().v1().customResourceDefinitions()
                .withName(name).get();
        if (crd == null) return null;
        Map<String, Object> result = mapCrd(crd);
        result.put("yaml", Serialization.asYaml(crd));

        // Add versions detail
        if (crd.getSpec().getVersions() != null) {
            result.put("versions", crd.getSpec().getVersions().stream().map(v -> {
                Map<String, Object> ver = new HashMap<>();
                ver.put("name", v.getName());
                ver.put("served", v.getServed());
                ver.put("storage", v.getStorage());
                return ver;
            }).collect(Collectors.toList()));
        }

        // Add conditions
        if (crd.getStatus() != null && crd.getStatus().getConditions() != null) {
            result.put("conditions", crd.getStatus().getConditions().stream().map(c -> {
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
    public List<Map<String, Object>> listCustomResources(Long clusterId, String group, String version, String plural, String namespace) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        var resources = namespace != null && !namespace.isEmpty()
                ? client.genericKubernetesResources(group + "/" + version, plural)
                    .inNamespace(namespace).list().getItems()
                : client.genericKubernetesResources(group + "/" + version, plural)
                    .inAnyNamespace().list().getItems();

        return resources.stream().map(this::mapGenericResource).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getCustomResource(Long clusterId, String group, String version, String plural, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        GenericKubernetesResource resource;
        if (namespace != null && !namespace.isEmpty()) {
            resource = client.genericKubernetesResources(group + "/" + version, plural)
                    .inNamespace(namespace).withName(name).get();
        } else {
            resource = client.genericKubernetesResources(group + "/" + version, plural)
                    .withName(name).get();
        }
        if (resource == null) return null;
        Map<String, Object> result = mapGenericResource(resource);
        result.put("yaml", Serialization.asYaml(resource));
        result.put("spec", resource.getAdditionalProperties().get("spec"));
        result.put("status", resource.getAdditionalProperties().get("status"));
        return result;
    }

    @Override
    public void deleteCustomResource(Long clusterId, String group, String version, String plural, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        if (namespace != null && !namespace.isEmpty()) {
            client.genericKubernetesResources(group + "/" + version, plural)
                    .inNamespace(namespace).withName(name).delete();
        } else {
            client.genericKubernetesResources(group + "/" + version, plural)
                    .withName(name).delete();
        }
    }

    private Map<String, Object> mapCrd(CustomResourceDefinition crd) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", crd.getMetadata().getName());
        map.put("createdAt", crd.getMetadata().getCreationTimestamp());
        if (crd.getSpec() != null) {
            map.put("group", crd.getSpec().getGroup());
            map.put("scope", crd.getSpec().getScope());
            if (crd.getSpec().getNames() != null) {
                map.put("kind", crd.getSpec().getNames().getKind());
                map.put("plural", crd.getSpec().getNames().getPlural());
                map.put("singular", crd.getSpec().getNames().getSingular());
                map.put("shortNames", crd.getSpec().getNames().getShortNames());
            }
            if (crd.getSpec().getVersions() != null && !crd.getSpec().getVersions().isEmpty()) {
                map.put("version", crd.getSpec().getVersions().stream()
                        .filter(v -> Boolean.TRUE.equals(v.getStorage()))
                        .findFirst()
                        .map(v -> v.getName())
                        .orElse(crd.getSpec().getVersions().get(0).getName()));
                map.put("versionCount", crd.getSpec().getVersions().size());
            }
        }
        // Status
        if (crd.getStatus() != null) {
            map.put("acceptedNames", crd.getStatus().getAcceptedNames());
        }
        return map;
    }

    private Map<String, Object> mapGenericResource(GenericKubernetesResource resource) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", resource.getMetadata().getName());
        map.put("namespace", resource.getMetadata().getNamespace());
        map.put("kind", resource.getKind());
        map.put("apiVersion", resource.getApiVersion());
        map.put("labels", resource.getMetadata().getLabels());
        map.put("createdAt", resource.getMetadata().getCreationTimestamp());
        return map;
    }
}
