package com.synapxnet.aiopsk8sservice.service.impl;

import com.synapxnet.aiopsk8sservice.service.K8sClientFactory;
import com.synapxnet.aiopsk8sservice.service.K8sNetworkPolicyService;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class K8sNetworkPolicyServiceImpl implements K8sNetworkPolicyService {

    private final K8sClientFactory clientFactory;

    public K8sNetworkPolicyServiceImpl(K8sClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public List<Map<String, Object>> listNetworkPolicies(Long clusterId, String namespace) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        return client.network().networkPolicies().inNamespace(namespace).list().getItems().stream()
                .map(this::mapNetworkPolicy)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getNetworkPolicy(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        NetworkPolicy np = client.network().networkPolicies().inNamespace(namespace).withName(name).get();
        if (np == null) return null;
        Map<String, Object> result = mapNetworkPolicy(np);
        result.put("yaml", Serialization.asYaml(np));
        // Add detailed spec
        if (np.getSpec() != null) {
            Map<String, Object> spec = new HashMap<>();
            spec.put("podSelector", np.getSpec().getPodSelector());
            spec.put("policyTypes", np.getSpec().getPolicyTypes());
            if (np.getSpec().getIngress() != null) {
                spec.put("ingressRules", np.getSpec().getIngress().stream().map(rule -> {
                    Map<String, Object> r = new HashMap<>();
                    r.put("from", rule.getFrom());
                    r.put("ports", rule.getPorts());
                    return r;
                }).collect(Collectors.toList()));
            }
            if (np.getSpec().getEgress() != null) {
                spec.put("egressRules", np.getSpec().getEgress().stream().map(rule -> {
                    Map<String, Object> r = new HashMap<>();
                    r.put("to", rule.getTo());
                    r.put("ports", rule.getPorts());
                    return r;
                }).collect(Collectors.toList()));
            }
            result.put("spec", spec);
        }
        return result;
    }

    @Override
    public void createNetworkPolicyFromYaml(Long clusterId, String namespace, String yaml) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        NetworkPolicy np = Serialization.unmarshal(yaml, NetworkPolicy.class);
        np.getMetadata().setNamespace(namespace);
        client.network().networkPolicies().inNamespace(namespace).resource(np).create();
    }

    @Override
    public void deleteNetworkPolicy(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.network().networkPolicies().inNamespace(namespace).withName(name).delete();
    }

    private Map<String, Object> mapNetworkPolicy(NetworkPolicy np) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", np.getMetadata().getName());
        map.put("namespace", np.getMetadata().getNamespace());
        map.put("labels", np.getMetadata().getLabels());
        map.put("createdAt", np.getMetadata().getCreationTimestamp());
        if (np.getSpec() != null) {
            map.put("policyTypes", np.getSpec().getPolicyTypes());
            map.put("ingressRuleCount", np.getSpec().getIngress() != null ? np.getSpec().getIngress().size() : 0);
            map.put("egressRuleCount", np.getSpec().getEgress() != null ? np.getSpec().getEgress().size() : 0);
        }
        return map;
    }
}
