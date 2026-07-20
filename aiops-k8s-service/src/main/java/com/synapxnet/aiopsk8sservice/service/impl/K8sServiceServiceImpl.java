package com.synapxnet.aiopsk8sservice.service.impl;

import com.synapxnet.aiopsk8sservice.exception.K8sResourceNotFoundException;
import com.synapxnet.aiopsk8sservice.service.K8sClientFactory;
import com.synapxnet.aiopsk8sservice.service.K8sServiceService;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class K8sServiceServiceImpl implements K8sServiceService {

    private final K8sClientFactory clientFactory;

    public K8sServiceServiceImpl(K8sClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public List<Map<String, Object>> listServices(Long clusterId, String namespace) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        List<io.fabric8.kubernetes.api.model.Service> services;
        if (namespace == null || namespace.isEmpty()) {
            services = client.services().inAnyNamespace().list().getItems();
        } else {
            services = client.services().inNamespace(namespace).list().getItems();
        }
        return services.stream().map(this::serviceToMap).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getService(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        io.fabric8.kubernetes.api.model.Service svc = client.services().inNamespace(namespace).withName(name).get();
        if (svc == null) {
            throw new K8sResourceNotFoundException("Service not found: " + namespace + "/" + name);
        }
        Map<String, Object> map = serviceToMap(svc);
        map.put("labels", svc.getMetadata().getLabels());
        map.put("annotations", svc.getMetadata().getAnnotations());
        map.put("yaml", Serialization.asYaml(svc));
        return map;
    }

    @Override
    public void createService(Long clusterId, String namespace, String yaml) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        io.fabric8.kubernetes.api.model.Service svc = Serialization.unmarshal(yaml, io.fabric8.kubernetes.api.model.Service.class);
        if (svc.getMetadata().getNamespace() == null) {
            svc.getMetadata().setNamespace(namespace);
        }
        client.services().inNamespace(namespace).resource(svc).create();
    }

    @Override
    public void updateService(Long clusterId, String namespace, String name, String yaml) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        io.fabric8.kubernetes.api.model.Service svc = Serialization.unmarshal(yaml, io.fabric8.kubernetes.api.model.Service.class);
        svc.getMetadata().setName(name);
        svc.getMetadata().setNamespace(namespace);
        client.services().inNamespace(namespace).resource(svc).update();
    }

    @Override
    public void deleteService(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.services().inNamespace(namespace).withName(name).delete();
    }

    @Override
    public List<Map<String, Object>> getEndpoints(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        Endpoints endpoints = client.endpoints().inNamespace(namespace).withName(name).get();
        if (endpoints == null) return Collections.emptyList();

        List<Map<String, Object>> result = new ArrayList<>();
        if (endpoints.getSubsets() != null) {
            for (EndpointSubset subset : endpoints.getSubsets()) {
                List<String> ports = subset.getPorts() != null
                        ? subset.getPorts().stream().map(p -> p.getPort() + "/" + p.getProtocol()).collect(Collectors.toList())
                        : Collections.emptyList();

                if (subset.getAddresses() != null) {
                    for (EndpointAddress addr : subset.getAddresses()) {
                        Map<String, Object> ep = new HashMap<>();
                        ep.put("ip", addr.getIp());
                        ep.put("nodeName", addr.getNodeName());
                        ep.put("ports", ports);
                        if (addr.getTargetRef() != null) {
                            ep.put("podName", addr.getTargetRef().getName());
                            ep.put("podNamespace", addr.getTargetRef().getNamespace());
                        }
                        ep.put("ready", true);
                        result.add(ep);
                    }
                }
                if (subset.getNotReadyAddresses() != null) {
                    for (EndpointAddress addr : subset.getNotReadyAddresses()) {
                        Map<String, Object> ep = new HashMap<>();
                        ep.put("ip", addr.getIp());
                        ep.put("nodeName", addr.getNodeName());
                        ep.put("ports", ports);
                        if (addr.getTargetRef() != null) {
                            ep.put("podName", addr.getTargetRef().getName());
                        }
                        ep.put("ready", false);
                        result.add(ep);
                    }
                }
            }
        }
        return result;
    }

    private Map<String, Object> serviceToMap(io.fabric8.kubernetes.api.model.Service svc) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", svc.getMetadata().getName());
        map.put("namespace", svc.getMetadata().getNamespace());
        map.put("createdAt", svc.getMetadata().getCreationTimestamp());

        ServiceSpec spec = svc.getSpec();
        if (spec != null) {
            map.put("type", spec.getType());
            map.put("clusterIP", spec.getClusterIP());
            map.put("selector", spec.getSelector());

            if (spec.getPorts() != null) {
                List<Map<String, Object>> ports = spec.getPorts().stream().map(p -> {
                    Map<String, Object> portMap = new HashMap<>();
                    portMap.put("name", p.getName());
                    portMap.put("port", p.getPort());
                    portMap.put("targetPort", p.getTargetPort() != null ? p.getTargetPort().getValue() : null);
                    portMap.put("nodePort", p.getNodePort());
                    portMap.put("protocol", p.getProtocol());
                    return portMap;
                }).collect(Collectors.toList());
                map.put("ports", ports);
            }

            if (spec.getExternalIPs() != null && !spec.getExternalIPs().isEmpty()) {
                map.put("externalIPs", spec.getExternalIPs());
            }

            if ("LoadBalancer".equals(spec.getType()) && svc.getStatus() != null
                    && svc.getStatus().getLoadBalancer() != null
                    && svc.getStatus().getLoadBalancer().getIngress() != null) {
                List<String> lbIPs = svc.getStatus().getLoadBalancer().getIngress().stream()
                        .map(ingress -> ingress.getIp() != null ? ingress.getIp() : ingress.getHostname())
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                map.put("loadBalancerIPs", lbIPs);
            }
        }

        return map;
    }
}
