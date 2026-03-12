package com.synapxnet.aiopsk8sservice.service;

import java.util.List;
import java.util.Map;

public interface K8sRbacService {
    // ClusterRoles
    List<Map<String, Object>> listClusterRoles(Long clusterId);
    Map<String, Object> getClusterRole(Long clusterId, String name);
    void deleteClusterRole(Long clusterId, String name);

    // ClusterRoleBindings
    List<Map<String, Object>> listClusterRoleBindings(Long clusterId);
    Map<String, Object> getClusterRoleBinding(Long clusterId, String name);
    void deleteClusterRoleBinding(Long clusterId, String name);

    // Roles
    List<Map<String, Object>> listRoles(Long clusterId, String namespace);
    Map<String, Object> getRole(Long clusterId, String namespace, String name);
    void deleteRole(Long clusterId, String namespace, String name);

    // RoleBindings
    List<Map<String, Object>> listRoleBindings(Long clusterId, String namespace);
    Map<String, Object> getRoleBinding(Long clusterId, String namespace, String name);
    void deleteRoleBinding(Long clusterId, String namespace, String name);

    // ServiceAccounts
    List<Map<String, Object>> listServiceAccounts(Long clusterId, String namespace);
    Map<String, Object> getServiceAccount(Long clusterId, String namespace, String name);
    void deleteServiceAccount(Long clusterId, String namespace, String name);
}
