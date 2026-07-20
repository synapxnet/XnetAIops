package com.synapxnet.aiopsregservice.service.impl;

import com.synapxnet.aiopsregservice.client.K8sHelmClient;
import com.synapxnet.aiopsregservice.entity.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Consumer;

@Service
public class K8sDeployService {

    private static final Logger log = LoggerFactory.getLogger(K8sDeployService.class);

    private final K8sHelmClient k8sHelmClient;

    public K8sDeployService(K8sHelmClient k8sHelmClient) {
        this.k8sHelmClient = k8sHelmClient;
    }

    public void deploy(Registry registry, Consumer<String> logCallback) {
        logCallback.accept("Deploying " + registry.getRegistryType() + " via Helm to cluster " + registry.getClusterId());

        String chartName = getChartName(registry.getRegistryType());
        String chartVersion = registry.getVersion() != null ? registry.getVersion() : "";
        String namespace = registry.getNamespace() != null ? registry.getNamespace() : "default";
        String releaseName = registry.getReleaseName() != null ? registry.getReleaseName()
                : "reg-" + registry.getRegistryType() + "-" + registry.getId();
        String values = registry.getHelmValues() != null ? registry.getHelmValues() : getDefaultValues(registry);

        logCallback.accept("Chart: " + chartName + ", version: " + chartVersion);
        logCallback.accept("Namespace: " + namespace + ", release: " + releaseName);

        try {
            Map<String, Object> result = k8sHelmClient.installRelease(
                    registry.getClusterId(), namespace, releaseName, chartName, chartVersion, null, values);
            logCallback.accept("Helm install result: " + result);
            logCallback.accept("K8s deployment completed successfully");
        } catch (Exception e) {
            throw new RuntimeException("K8s deployment failed: " + e.getMessage(), e);
        }
    }

    public void undeploy(Registry registry, Consumer<String> logCallback) {
        logCallback.accept("Uninstalling Helm release for registry " + registry.getId());
        String namespace = registry.getNamespace() != null ? registry.getNamespace() : "default";
        String releaseName = registry.getReleaseName() != null ? registry.getReleaseName()
                : "reg-" + registry.getRegistryType() + "-" + registry.getId();

        try {
            k8sHelmClient.uninstallRelease(registry.getClusterId(), releaseName, namespace);
            logCallback.accept("Helm uninstall completed");
        } catch (Exception e) {
            throw new RuntimeException("K8s undeploy failed: " + e.getMessage(), e);
        }
    }

    public void upgrade(Registry registry, Consumer<String> logCallback) {
        logCallback.accept("Upgrading Helm release for registry " + registry.getId());
        String namespace = registry.getNamespace() != null ? registry.getNamespace() : "default";
        String releaseName = registry.getReleaseName() != null ? registry.getReleaseName()
                : "reg-" + registry.getRegistryType() + "-" + registry.getId();
        String chartVersion = registry.getVersion() != null ? registry.getVersion() : "";
        String values = registry.getHelmValues() != null ? registry.getHelmValues() : getDefaultValues(registry);

        try {
            k8sHelmClient.upgradeRelease(registry.getClusterId(), releaseName, namespace, chartVersion, values);
            logCallback.accept("Helm upgrade completed");
        } catch (Exception e) {
            throw new RuntimeException("K8s upgrade failed: " + e.getMessage(), e);
        }
    }

    private String getChartName(String registryType) {
        switch (registryType) {
            case "harbor":
                return "harbor";
            case "gitlab":
                return "gitlab";
            case "docker_distribution":
                return "docker-registry";
            default:
                throw new IllegalArgumentException("Unknown registry type: " + registryType);
        }
    }

    private String getDefaultValues(Registry registry) {
        String adminPass = registry.getEncryptedAdminPassword() != null ? registry.getEncryptedAdminPassword() : "Harbor12345";
        switch (registry.getRegistryType()) {
            case "harbor":
                return "expose:\n  type: nodePort\n  tls:\n    enabled: false\n" +
                       "harborAdminPassword: " + adminPass + "\n" +
                       "externalURL: " + (registry.getEndpoint() != null ? registry.getEndpoint() : "http://harbor.local") + "\n";
            case "gitlab":
                return "global:\n  edition: ce\n  hosts:\n    domain: " +
                       (registry.getEndpoint() != null ? registry.getEndpoint() : "gitlab.local") + "\n" +
                       "  initialRootPassword: " + adminPass + "\n";
            case "docker_distribution":
                return "replicaCount: 1\nstorage: filesystem\n";
            default:
                return "";
        }
    }
}
