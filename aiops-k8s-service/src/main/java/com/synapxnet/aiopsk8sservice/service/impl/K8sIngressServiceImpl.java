package com.synapxnet.aiopsk8sservice.service.impl;

import com.synapxnet.aiopsk8sservice.exception.K8sResourceNotFoundException;
import com.synapxnet.aiopsk8sservice.service.K8sClientFactory;
import com.synapxnet.aiopsk8sservice.service.K8sIngressService;
import io.fabric8.kubernetes.api.model.networking.v1.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class K8sIngressServiceImpl implements K8sIngressService {

    private final K8sClientFactory clientFactory;

    public K8sIngressServiceImpl(K8sClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public List<Map<String, Object>> listIngresses(Long clusterId, String namespace) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        List<Ingress> ingresses;
        if (namespace == null || namespace.isEmpty()) {
            ingresses = client.network().v1().ingresses().inAnyNamespace().list().getItems();
        } else {
            ingresses = client.network().v1().ingresses().inNamespace(namespace).list().getItems();
        }
        return ingresses.stream().map(this::ingressToMap).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getIngress(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        Ingress ingress = client.network().v1().ingresses().inNamespace(namespace).withName(name).get();
        if (ingress == null) {
            throw new K8sResourceNotFoundException("Ingress not found: " + namespace + "/" + name);
        }
        Map<String, Object> map = ingressToMap(ingress);
        map.put("labels", ingress.getMetadata().getLabels());
        map.put("annotations", ingress.getMetadata().getAnnotations());
        map.put("yaml", Serialization.asYaml(ingress));
        return map;
    }

    @Override
    public void createIngress(Long clusterId, String namespace, String yaml) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        Ingress ingress = Serialization.unmarshal(yaml, Ingress.class);
        if (ingress.getMetadata().getNamespace() == null) {
            ingress.getMetadata().setNamespace(namespace);
        }
        client.network().v1().ingresses().inNamespace(namespace).resource(ingress).create();
    }

    @Override
    public void updateIngress(Long clusterId, String namespace, String name, String yaml) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        Ingress ingress = Serialization.unmarshal(yaml, Ingress.class);
        ingress.getMetadata().setName(name);
        ingress.getMetadata().setNamespace(namespace);
        client.network().v1().ingresses().inNamespace(namespace).resource(ingress).update();
    }

    @Override
    public void deleteIngress(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.network().v1().ingresses().inNamespace(namespace).withName(name).delete();
    }

    private Map<String, Object> ingressToMap(Ingress ingress) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", ingress.getMetadata().getName());
        map.put("namespace", ingress.getMetadata().getNamespace());
        map.put("createdAt", ingress.getMetadata().getCreationTimestamp());

        IngressSpec spec = ingress.getSpec();
        if (spec != null) {
            map.put("ingressClassName", spec.getIngressClassName());

            // TLS
            if (spec.getTls() != null && !spec.getTls().isEmpty()) {
                List<Map<String, Object>> tls = spec.getTls().stream().map(t -> {
                    Map<String, Object> tlsMap = new HashMap<>();
                    tlsMap.put("hosts", t.getHosts());
                    tlsMap.put("secretName", t.getSecretName());
                    return tlsMap;
                }).collect(Collectors.toList());
                map.put("tls", tls);
            }

            // Rules
            if (spec.getRules() != null) {
                List<Map<String, Object>> rules = spec.getRules().stream().map(rule -> {
                    Map<String, Object> ruleMap = new HashMap<>();
                    ruleMap.put("host", rule.getHost());
                    if (rule.getHttp() != null && rule.getHttp().getPaths() != null) {
                        List<Map<String, Object>> paths = rule.getHttp().getPaths().stream().map(p -> {
                            Map<String, Object> pathMap = new HashMap<>();
                            pathMap.put("path", p.getPath());
                            pathMap.put("pathType", p.getPathType());
                            if (p.getBackend() != null && p.getBackend().getService() != null) {
                                pathMap.put("serviceName", p.getBackend().getService().getName());
                                if (p.getBackend().getService().getPort() != null) {
                                    pathMap.put("servicePort", p.getBackend().getService().getPort().getNumber() != null
                                            ? p.getBackend().getService().getPort().getNumber()
                                            : p.getBackend().getService().getPort().getName());
                                }
                            }
                            return pathMap;
                        }).collect(Collectors.toList());
                        ruleMap.put("paths", paths);
                    }
                    return ruleMap;
                }).collect(Collectors.toList());
                map.put("rules", rules);

                // Extract hosts for display
                List<String> hosts = spec.getRules().stream()
                        .map(IngressRule::getHost).filter(Objects::nonNull).collect(Collectors.toList());
                map.put("hosts", hosts);
            }
        }

        // LoadBalancer IPs
        if (ingress.getStatus() != null && ingress.getStatus().getLoadBalancer() != null
                && ingress.getStatus().getLoadBalancer().getIngress() != null) {
            List<String> addresses = ingress.getStatus().getLoadBalancer().getIngress().stream()
                    .map(lb -> lb.getIp() != null ? lb.getIp() : lb.getHostname())
                    .filter(Objects::nonNull).collect(Collectors.toList());
            map.put("addresses", addresses);
        }

        return map;
    }
}
