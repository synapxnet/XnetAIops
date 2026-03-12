package com.synapxnet.aiopsk8sservice.service.impl;

import com.synapxnet.aiopsk8sservice.exception.K8sResourceNotFoundException;
import com.synapxnet.aiopsk8sservice.service.K8sClientFactory;
import com.synapxnet.aiopsk8sservice.service.K8sConfigService;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class K8sConfigServiceImpl implements K8sConfigService {

    private final K8sClientFactory clientFactory;

    public K8sConfigServiceImpl(K8sClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    // ====== ConfigMaps ======

    @Override
    public List<Map<String, Object>> listConfigMaps(Long clusterId, String namespace) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        List<ConfigMap> configMaps;
        if (namespace == null || namespace.isEmpty()) {
            configMaps = client.configMaps().inAnyNamespace().list().getItems();
        } else {
            configMaps = client.configMaps().inNamespace(namespace).list().getItems();
        }
        return configMaps.stream().map(cm -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", cm.getMetadata().getName());
            map.put("namespace", cm.getMetadata().getNamespace());
            map.put("dataCount", cm.getData() != null ? cm.getData().size() : 0);
            map.put("createdAt", cm.getMetadata().getCreationTimestamp());
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getConfigMap(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        ConfigMap cm = client.configMaps().inNamespace(namespace).withName(name).get();
        if (cm == null) {
            throw new K8sResourceNotFoundException("ConfigMap not found: " + namespace + "/" + name);
        }
        Map<String, Object> map = new HashMap<>();
        map.put("name", cm.getMetadata().getName());
        map.put("namespace", cm.getMetadata().getNamespace());
        map.put("data", cm.getData());
        map.put("binaryData", cm.getBinaryData() != null ? cm.getBinaryData().keySet() : Collections.emptyList());
        map.put("labels", cm.getMetadata().getLabels());
        map.put("annotations", cm.getMetadata().getAnnotations());
        map.put("createdAt", cm.getMetadata().getCreationTimestamp());
        map.put("yaml", Serialization.asYaml(cm));
        return map;
    }

    @Override
    public void createConfigMap(Long clusterId, String namespace, String name, Map<String, String> data) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        ConfigMap cm = new ConfigMapBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                .endMetadata()
                .withData(data)
                .build();
        client.configMaps().inNamespace(namespace).resource(cm).create();
    }

    @Override
    public void updateConfigMap(Long clusterId, String namespace, String name, Map<String, String> data) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.configMaps().inNamespace(namespace).withName(name).edit(cm -> {
            cm.setData(data);
            return cm;
        });
    }

    @Override
    public void deleteConfigMap(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.configMaps().inNamespace(namespace).withName(name).delete();
    }

    // ====== Secrets ======

    @Override
    public List<Map<String, Object>> listSecrets(Long clusterId, String namespace) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        List<Secret> secrets;
        if (namespace == null || namespace.isEmpty()) {
            secrets = client.secrets().inAnyNamespace().list().getItems();
        } else {
            secrets = client.secrets().inNamespace(namespace).list().getItems();
        }
        return secrets.stream().map(secret -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", secret.getMetadata().getName());
            map.put("namespace", secret.getMetadata().getNamespace());
            map.put("type", secret.getType());
            map.put("dataCount", secret.getData() != null ? secret.getData().size() : 0);
            map.put("createdAt", secret.getMetadata().getCreationTimestamp());
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getSecret(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        Secret secret = client.secrets().inNamespace(namespace).withName(name).get();
        if (secret == null) {
            throw new K8sResourceNotFoundException("Secret not found: " + namespace + "/" + name);
        }
        Map<String, Object> map = new HashMap<>();
        map.put("name", secret.getMetadata().getName());
        map.put("namespace", secret.getMetadata().getNamespace());
        map.put("type", secret.getType());
        // Return keys only, not values (for security)
        map.put("dataKeys", secret.getData() != null ? secret.getData().keySet() : Collections.emptySet());
        map.put("data", secret.getData()); // base64 encoded
        map.put("labels", secret.getMetadata().getLabels());
        map.put("annotations", secret.getMetadata().getAnnotations());
        map.put("createdAt", secret.getMetadata().getCreationTimestamp());
        map.put("yaml", Serialization.asYaml(secret));
        return map;
    }

    @Override
    public void createSecret(Long clusterId, String namespace, String name, String type, Map<String, String> data) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        // Data should be base64 encoded
        Map<String, String> encodedData = new HashMap<>();
        if (data != null) {
            data.forEach((k, v) -> encodedData.put(k, Base64.getEncoder().encodeToString(v.getBytes())));
        }
        Secret secret = new SecretBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                .endMetadata()
                .withType(type != null ? type : "Opaque")
                .withData(encodedData)
                .build();
        client.secrets().inNamespace(namespace).resource(secret).create();
    }

    @Override
    public void updateSecret(Long clusterId, String namespace, String name, Map<String, String> data) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        Map<String, String> encodedData = new HashMap<>();
        if (data != null) {
            data.forEach((k, v) -> encodedData.put(k, Base64.getEncoder().encodeToString(v.getBytes())));
        }
        client.secrets().inNamespace(namespace).withName(name).edit(secret -> {
            secret.setData(encodedData);
            return secret;
        });
    }

    @Override
    public void deleteSecret(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.secrets().inNamespace(namespace).withName(name).delete();
    }
}
