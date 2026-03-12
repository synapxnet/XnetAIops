package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.service.K8sRbacService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/clusters/{clusterId}/rbac")
public class K8sRbacController {

    private final K8sRbacService rbacService;

    public K8sRbacController(K8sRbacService rbacService) {
        this.rbacService = rbacService;
    }

    // ====== ClusterRoles ======
    @GetMapping("/clusterroles")
    public Result<List<Map<String, Object>>> listClusterRoles(@PathVariable Long clusterId) {
        return Result.success(rbacService.listClusterRoles(clusterId));
    }

    @GetMapping("/clusterroles/{name}")
    public Result<Map<String, Object>> getClusterRole(@PathVariable Long clusterId, @PathVariable String name) {
        return Result.success(rbacService.getClusterRole(clusterId, name));
    }

    @DeleteMapping("/clusterroles/{name}")
    public Result<Void> deleteClusterRole(@PathVariable Long clusterId, @PathVariable String name) {
        rbacService.deleteClusterRole(clusterId, name);
        return Result.success();
    }

    // ====== ClusterRoleBindings ======
    @GetMapping("/clusterrolebindings")
    public Result<List<Map<String, Object>>> listClusterRoleBindings(@PathVariable Long clusterId) {
        return Result.success(rbacService.listClusterRoleBindings(clusterId));
    }

    @GetMapping("/clusterrolebindings/{name}")
    public Result<Map<String, Object>> getClusterRoleBinding(@PathVariable Long clusterId, @PathVariable String name) {
        return Result.success(rbacService.getClusterRoleBinding(clusterId, name));
    }

    @DeleteMapping("/clusterrolebindings/{name}")
    public Result<Void> deleteClusterRoleBinding(@PathVariable Long clusterId, @PathVariable String name) {
        rbacService.deleteClusterRoleBinding(clusterId, name);
        return Result.success();
    }

    // ====== Roles (namespace-scoped) ======
    @GetMapping("/namespaces/{namespace}/roles")
    public Result<List<Map<String, Object>>> listRoles(@PathVariable Long clusterId, @PathVariable String namespace) {
        return Result.success(rbacService.listRoles(clusterId, namespace));
    }

    @GetMapping("/namespaces/{namespace}/roles/{name}")
    public Result<Map<String, Object>> getRole(@PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        return Result.success(rbacService.getRole(clusterId, namespace, name));
    }

    @DeleteMapping("/namespaces/{namespace}/roles/{name}")
    public Result<Void> deleteRole(@PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        rbacService.deleteRole(clusterId, namespace, name);
        return Result.success();
    }

    // ====== RoleBindings (namespace-scoped) ======
    @GetMapping("/namespaces/{namespace}/rolebindings")
    public Result<List<Map<String, Object>>> listRoleBindings(@PathVariable Long clusterId, @PathVariable String namespace) {
        return Result.success(rbacService.listRoleBindings(clusterId, namespace));
    }

    @GetMapping("/namespaces/{namespace}/rolebindings/{name}")
    public Result<Map<String, Object>> getRoleBinding(@PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        return Result.success(rbacService.getRoleBinding(clusterId, namespace, name));
    }

    @DeleteMapping("/namespaces/{namespace}/rolebindings/{name}")
    public Result<Void> deleteRoleBinding(@PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        rbacService.deleteRoleBinding(clusterId, namespace, name);
        return Result.success();
    }

    // ====== ServiceAccounts (namespace-scoped) ======
    @GetMapping("/namespaces/{namespace}/serviceaccounts")
    public Result<List<Map<String, Object>>> listServiceAccounts(@PathVariable Long clusterId, @PathVariable String namespace) {
        return Result.success(rbacService.listServiceAccounts(clusterId, namespace));
    }

    @GetMapping("/namespaces/{namespace}/serviceaccounts/{name}")
    public Result<Map<String, Object>> getServiceAccount(@PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        return Result.success(rbacService.getServiceAccount(clusterId, namespace, name));
    }

    @DeleteMapping("/namespaces/{namespace}/serviceaccounts/{name}")
    public Result<Void> deleteServiceAccount(@PathVariable Long clusterId, @PathVariable String namespace, @PathVariable String name) {
        rbacService.deleteServiceAccount(clusterId, namespace, name);
        return Result.success();
    }
}
