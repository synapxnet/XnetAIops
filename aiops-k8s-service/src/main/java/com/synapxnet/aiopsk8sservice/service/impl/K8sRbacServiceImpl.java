package com.synapxnet.aiopsk8sservice.service.impl;

import com.synapxnet.aiopsk8sservice.service.K8sClientFactory;
import com.synapxnet.aiopsk8sservice.service.K8sRbacService;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.rbac.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class K8sRbacServiceImpl implements K8sRbacService {

    private final K8sClientFactory clientFactory;

    public K8sRbacServiceImpl(K8sClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public List<Map<String, Object>> listClusterRoles(Long clusterId) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        return client.rbac().clusterRoles().list().getItems().stream()
                .map(this::mapClusterRole)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getClusterRole(Long clusterId, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        ClusterRole cr = client.rbac().clusterRoles().withName(name).get();
        if (cr == null) return null;
        Map<String, Object> result = mapClusterRole(cr);
        result.put("rules", mapPolicyRules(cr.getRules()));
        result.put("yaml", Serialization.asYaml(cr));
        return result;
    }

    @Override
    public void deleteClusterRole(Long clusterId, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.rbac().clusterRoles().withName(name).delete();
    }

    @Override
    public List<Map<String, Object>> listClusterRoleBindings(Long clusterId) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        return client.rbac().clusterRoleBindings().list().getItems().stream()
                .map(this::mapClusterRoleBinding)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getClusterRoleBinding(Long clusterId, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        ClusterRoleBinding crb = client.rbac().clusterRoleBindings().withName(name).get();
        if (crb == null) return null;
        Map<String, Object> result = mapClusterRoleBinding(crb);
        result.put("yaml", Serialization.asYaml(crb));
        return result;
    }

    @Override
    public void deleteClusterRoleBinding(Long clusterId, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.rbac().clusterRoleBindings().withName(name).delete();
    }

    @Override
    public List<Map<String, Object>> listRoles(Long clusterId, String namespace) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        return client.rbac().roles().inNamespace(namespace).list().getItems().stream()
                .map(this::mapRole)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getRole(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        Role role = client.rbac().roles().inNamespace(namespace).withName(name).get();
        if (role == null) return null;
        Map<String, Object> result = mapRole(role);
        result.put("rules", mapPolicyRules(role.getRules()));
        result.put("yaml", Serialization.asYaml(role));
        return result;
    }

    @Override
    public void deleteRole(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.rbac().roles().inNamespace(namespace).withName(name).delete();
    }

    @Override
    public List<Map<String, Object>> listRoleBindings(Long clusterId, String namespace) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        return client.rbac().roleBindings().inNamespace(namespace).list().getItems().stream()
                .map(this::mapRoleBinding)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getRoleBinding(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        RoleBinding rb = client.rbac().roleBindings().inNamespace(namespace).withName(name).get();
        if (rb == null) return null;
        Map<String, Object> result = mapRoleBinding(rb);
        result.put("yaml", Serialization.asYaml(rb));
        return result;
    }

    @Override
    public void deleteRoleBinding(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.rbac().roleBindings().inNamespace(namespace).withName(name).delete();
    }

    @Override
    public List<Map<String, Object>> listServiceAccounts(Long clusterId, String namespace) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        return client.serviceAccounts().inNamespace(namespace).list().getItems().stream()
                .map(this::mapServiceAccount)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getServiceAccount(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        ServiceAccount sa = client.serviceAccounts().inNamespace(namespace).withName(name).get();
        if (sa == null) return null;
        Map<String, Object> result = mapServiceAccount(sa);
        result.put("yaml", Serialization.asYaml(sa));
        return result;
    }

    @Override
    public void deleteServiceAccount(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.serviceAccounts().inNamespace(namespace).withName(name).delete();
    }

    private Map<String, Object> mapClusterRole(ClusterRole cr) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", cr.getMetadata().getName());
        map.put("labels", cr.getMetadata().getLabels());
        map.put("annotations", cr.getMetadata().getAnnotations());
        map.put("createdAt", cr.getMetadata().getCreationTimestamp());
        map.put("ruleCount", cr.getRules() != null ? cr.getRules().size() : 0);
        return map;
    }

    private Map<String, Object> mapClusterRoleBinding(ClusterRoleBinding crb) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", crb.getMetadata().getName());
        map.put("roleRef", Map.of(
                "kind", crb.getRoleRef().getKind(),
                "name", crb.getRoleRef().getName(),
                "apiGroup", crb.getRoleRef().getApiGroup()
        ));
        map.put("subjects", crb.getSubjects() != null ?
                crb.getSubjects().stream().map(s -> {
                    Map<String, String> subj = new HashMap<>();
                    subj.put("kind", s.getKind());
                    subj.put("name", s.getName());
                    subj.put("namespace", s.getNamespace());
                    return subj;
                }).collect(Collectors.toList()) : Collections.emptyList());
        map.put("createdAt", crb.getMetadata().getCreationTimestamp());
        return map;
    }

    private Map<String, Object> mapRole(Role role) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", role.getMetadata().getName());
        map.put("namespace", role.getMetadata().getNamespace());
        map.put("labels", role.getMetadata().getLabels());
        map.put("createdAt", role.getMetadata().getCreationTimestamp());
        map.put("ruleCount", role.getRules() != null ? role.getRules().size() : 0);
        return map;
    }

    private Map<String, Object> mapRoleBinding(RoleBinding rb) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", rb.getMetadata().getName());
        map.put("namespace", rb.getMetadata().getNamespace());
        map.put("roleRef", Map.of(
                "kind", rb.getRoleRef().getKind(),
                "name", rb.getRoleRef().getName(),
                "apiGroup", rb.getRoleRef().getApiGroup()
        ));
        map.put("subjects", rb.getSubjects() != null ?
                rb.getSubjects().stream().map(s -> {
                    Map<String, String> subj = new HashMap<>();
                    subj.put("kind", s.getKind());
                    subj.put("name", s.getName());
                    subj.put("namespace", s.getNamespace());
                    return subj;
                }).collect(Collectors.toList()) : Collections.emptyList());
        map.put("createdAt", rb.getMetadata().getCreationTimestamp());
        return map;
    }

    private Map<String, Object> mapServiceAccount(ServiceAccount sa) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", sa.getMetadata().getName());
        map.put("namespace", sa.getMetadata().getNamespace());
        map.put("labels", sa.getMetadata().getLabels());
        map.put("createdAt", sa.getMetadata().getCreationTimestamp());
        map.put("secretCount", sa.getSecrets() != null ? sa.getSecrets().size() : 0);
        return map;
    }

    private List<Map<String, Object>> mapPolicyRules(List<PolicyRule> rules) {
        if (rules == null) return Collections.emptyList();
        return rules.stream().map(r -> {
            Map<String, Object> rule = new HashMap<>();
            rule.put("apiGroups", r.getApiGroups());
            rule.put("resources", r.getResources());
            rule.put("verbs", r.getVerbs());
            rule.put("resourceNames", r.getResourceNames());
            rule.put("nonResourceURLs", r.getNonResourceURLs());
            return rule;
        }).collect(Collectors.toList());
    }
}
