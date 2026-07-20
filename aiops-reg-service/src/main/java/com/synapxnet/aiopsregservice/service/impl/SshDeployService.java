package com.synapxnet.aiopsregservice.service.impl;

import com.jcraft.jsch.Session;
import com.synapxnet.aiopsregservice.client.HomClient;
import com.synapxnet.aiopsregservice.client.HostInfo;
import com.synapxnet.aiopsregservice.entity.Registry;
import com.synapxnet.aiopsregservice.util.SshExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class SshDeployService {

    private static final Logger log = LoggerFactory.getLogger(SshDeployService.class);
    private static final int DEPLOY_TIMEOUT = 7200000; // 2 hours (Harbor offline installer is ~700MB)

    private final HomClient homClient;

    public SshDeployService(HomClient homClient) {
        this.homClient = homClient;
    }

    public void deploy(Registry registry, Consumer<String> logCallback) {
        Session session = null;
        try {
            session = connectToHost(registry);
            String scriptContent = loadScript(registry.getRegistryType());
            scriptContent = substituteVariables(scriptContent, registry);
            String remotePath = "/tmp/reg-deploy-" + registry.getUid() + ".sh";

            logCallback.accept("Uploading deploy script to " + registry.getHost() + ":" + remotePath);
            int exitCode = SshExecutor.uploadAndExecute(session, scriptContent, remotePath,
                    DEPLOY_TIMEOUT, logCallback, logCallback);

            if (exitCode != 0) {
                throw new RuntimeException("Deploy script exited with code " + exitCode);
            }
            logCallback.accept("Deployment completed successfully");
        } catch (Exception e) {
            throw new RuntimeException("SSH deployment failed: " + e.getMessage(), e);
        } finally {
            SshExecutor.disconnect(session);
        }
    }

    public void undeploy(Registry registry, Consumer<String> logCallback) {
        Session session = null;
        try {
            session = connectToHost(registry);
            String installPath = registry.getInstallPath() != null ? registry.getInstallPath() : "/opt/" + registry.getRegistryType();
            String command;
            switch (registry.getRegistryType()) {
                case "harbor":
                    command = "cd " + installPath + "/harbor && docker-compose down -v 2>&1; rm -rf " + installPath + "/harbor";
                    break;
                case "gitlab":
                    command = "docker stop gitlab 2>&1; docker rm gitlab 2>&1; rm -rf " + installPath + "/gitlab";
                    break;
                case "docker_distribution":
                    command = "docker stop registry 2>&1; docker rm registry 2>&1; rm -rf " + installPath + "/registry";
                    break;
                default:
                    throw new IllegalArgumentException("Unknown registry type: " + registry.getRegistryType());
            }
            logCallback.accept("Executing uninstall: " + command);
            String output = SshExecutor.executeCommand(session, command, DEPLOY_TIMEOUT);
            logCallback.accept(output);
            logCallback.accept("Uninstall completed");
        } catch (Exception e) {
            throw new RuntimeException("SSH undeploy failed: " + e.getMessage(), e);
        } finally {
            SshExecutor.disconnect(session);
        }
    }

    public void lifecycle(Registry registry, String action, Consumer<String> logCallback) {
        Session session = null;
        try {
            session = connectToHost(registry);
            String installPath = registry.getInstallPath() != null ? registry.getInstallPath() : "/opt/" + registry.getRegistryType();
            String command;
            switch (registry.getRegistryType()) {
                case "harbor":
                    command = "cd " + installPath + "/harbor && docker-compose " + action + " 2>&1";
                    break;
                case "gitlab":
                    command = "docker " + action + " gitlab 2>&1";
                    break;
                case "docker_distribution":
                    command = "docker " + action + " registry 2>&1";
                    break;
                default:
                    throw new IllegalArgumentException("Unknown registry type: " + registry.getRegistryType());
            }
            logCallback.accept("Executing " + action + ": " + command);
            String output = SshExecutor.executeCommand(session, command, DEPLOY_TIMEOUT);
            logCallback.accept(output);
            logCallback.accept(action + " completed");
        } catch (Exception e) {
            throw new RuntimeException("SSH " + action + " failed: " + e.getMessage(), e);
        } finally {
            SshExecutor.disconnect(session);
        }
    }

    public void upgrade(Registry registry, Consumer<String> logCallback) {
        // For SSH mode, upgrade means re-deploy with new version
        logCallback.accept("Upgrading registry (re-deploying with latest config)...");
        deploy(registry, logCallback);
    }

    private Session connectToHost(Registry registry) {
        try {
            // If host info is stored directly on registry
            if (registry.getHost() != null && registry.getSshUser() != null) {
                return SshExecutor.connect(
                        registry.getHost(),
                        registry.getSshPort() != null ? registry.getSshPort() : 22,
                        registry.getSshUser(),
                        registry.getEncryptedPassword(),
                        registry.getEncryptedPrivateKey()
                );
            }
            // Otherwise fetch from HOM
            if (registry.getHostId() != null) {
                HostInfo host = homClient.getHost(registry.getHostId());
                return SshExecutor.connect(
                        host.getIpAddress(),
                        host.getSshPort() != null ? host.getSshPort() : 22,
                        host.getSshUser(),
                        host.getEncryptedPassword(),
                        host.getPrivateKey()
                );
            }
            throw new IllegalArgumentException("No SSH host information configured for registry " + registry.getId());
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to host: " + e.getMessage(), e);
        }
    }

    private String loadScript(String registryType) {
        String filename;
        switch (registryType) {
            case "harbor":
                filename = "scripts/harbor-docker-compose.sh";
                break;
            case "gitlab":
                filename = "scripts/gitlab-docker.sh";
                break;
            case "docker_distribution":
                filename = "scripts/distribution-docker.sh";
                break;
            default:
                throw new IllegalArgumentException("Unknown registry type: " + registryType);
        }
        try {
            ClassPathResource resource = new ClassPathResource(filename);
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load deploy script: " + filename, e);
        }
    }

    private String substituteVariables(String script, Registry registry) {
        String installPath = registry.getInstallPath() != null ? registry.getInstallPath() : "/opt/" + registry.getRegistryType();
        String endpoint = registry.getEndpoint() != null ? registry.getEndpoint() : registry.getHost();
        String adminUser = registry.getAdminUser() != null ? registry.getAdminUser() : "admin";
        String adminPass = registry.getEncryptedAdminPassword() != null ? registry.getEncryptedAdminPassword() : "Harbor12345";
        String version = (registry.getVersion() != null && !registry.getVersion().isEmpty()) ? registry.getVersion() : "latest";

        String servicePort = String.valueOf(getDefaultPort(registry));

        return script
                .replace("${INSTALL_PATH}", installPath)
                .replace("${REGISTRY_HOST}", registry.getHost() != null ? registry.getHost() : "localhost")
                .replace("${REGISTRY_ENDPOINT}", endpoint)
                .replace("${ADMIN_USER}", adminUser)
                .replace("${ADMIN_PASSWORD}", adminPass)
                .replace("${VERSION}", version)
                .replace("${SERVICE_PORT}", servicePort)
                .replace("${USE_SSL}", Boolean.TRUE.equals(registry.getUseSsl()) ? "true" : "false");
    }

    private int getDefaultPort(Registry registry) {
        if (registry.getServicePort() != null && registry.getServicePort() > 0) {
            return registry.getServicePort();
        }
        switch (registry.getRegistryType()) {
            case "harbor": return 80;
            case "gitlab": return 80;
            case "docker_distribution": return 5000;
            default: return 80;
        }
    }

    /**
     * SSH into a host and check whether a port is occupied.
     * Returns {occupied: true/false, process: "..."}.
     */
    public Map<String, Object> checkPort(String host, int sshPort, String sshUser, String password, int port) {
        Session session = null;
        Map<String, Object> result = new HashMap<>();
        try {
            session = SshExecutor.connect(host, sshPort, sshUser, password, null);
            // Use ss to check port; fallback to netstat
            String cmd = "ss -tlnp 'sport = :" + port + "' 2>/dev/null || netstat -tlnp 2>/dev/null | grep ':" + port + " '";
            String output = SshExecutor.executeCommand(session, cmd, 10000);
            boolean occupied = output != null && !output.trim().isEmpty() && output.contains(":" + port);
            result.put("occupied", occupied);
            result.put("detail", occupied ? output.trim() : "");
            result.put("port", port);
            result.put("host", host);
        } catch (Exception e) {
            log.error("Port check failed for {}:{} - {}", host, port, e.getMessage());
            result.put("occupied", false);
            result.put("error", "无法连接主机: " + e.getMessage());
            result.put("port", port);
            result.put("host", host);
        } finally {
            SshExecutor.disconnect(session);
        }
        return result;
    }
}
