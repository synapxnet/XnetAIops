package com.synapxnet.aiopsregservice.service.impl;

import com.synapxnet.aiopsregservice.entity.DeployLog;
import com.synapxnet.aiopsregservice.entity.Registry;
import com.synapxnet.aiopsregservice.mapper.DeployLogMapper;
import com.synapxnet.aiopsregservice.mapper.RegistryMapper;
import com.synapxnet.aiopsregservice.service.RegistryDeployService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RegistryDeployServiceImpl implements RegistryDeployService {

    private static final Logger log = LoggerFactory.getLogger(RegistryDeployServiceImpl.class);

    /** Track registry IDs with cancelled deployments */
    private final Set<Long> cancelledSet = ConcurrentHashMap.newKeySet();

    private final RegistryMapper registryMapper;
    private final DeployLogMapper deployLogMapper;
    private final SshDeployService sshDeployService;
    private final K8sDeployService k8sDeployService;
    private final ApplicationContext applicationContext;

    public RegistryDeployServiceImpl(RegistryMapper registryMapper, DeployLogMapper deployLogMapper,
                                     SshDeployService sshDeployService, K8sDeployService k8sDeployService,
                                     ApplicationContext applicationContext) {
        this.registryMapper = registryMapper;
        this.deployLogMapper = deployLogMapper;
        this.sshDeployService = sshDeployService;
        this.k8sDeployService = k8sDeployService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void deploy(Long registryId) {
        Registry registry = getRegistry(registryId);
        if ("deploying".equals(registry.getStatus()) || "running".equals(registry.getStatus())) {
            throw new IllegalArgumentException("Registry is already " + registry.getStatus());
        }
        cancelledSet.remove(registryId);
        registryMapper.updateStatus(registryId, "deploying");
        // Use self-proxy for @Async
        applicationContext.getBean(RegistryDeployServiceImpl.class).asyncDeploy(registry);
    }

    @Async
    public void asyncDeploy(Registry registry) {
        DeployLog logEntry = createLog(registry.getId(), "install");
        StringBuilder logText = new StringBuilder();
        try {
            if ("ssh".equals(registry.getDeployMode())) {
                sshDeployService.deploy(registry, line -> logText.append(line).append("\n"));
            } else if ("k8s".equals(registry.getDeployMode())) {
                k8sDeployService.deploy(registry, line -> logText.append(line).append("\n"));
            } else {
                throw new IllegalArgumentException("Unknown deploy mode: " + registry.getDeployMode());
            }
            if (!isCancelled(registry.getId())) {
                registryMapper.updateStatus(registry.getId(), "running");
                finishLog(logEntry, "success", logText.toString());
            }
        } catch (Exception e) {
            if (!isCancelled(registry.getId())) {
                log.error("Deploy failed for registry {}: {}", registry.getId(), e.getMessage(), e);
                logText.append("\nERROR: ").append(e.getMessage());
                registryMapper.updateStatus(registry.getId(), "failed");
                finishLog(logEntry, "failed", logText.toString());
            }
        } finally {
            cancelledSet.remove(registry.getId());
        }
    }

    @Override
    public void undeploy(Long registryId) {
        Registry registry = getRegistry(registryId);
        registryMapper.updateStatus(registryId, "uninstalling");
        applicationContext.getBean(RegistryDeployServiceImpl.class).asyncUndeploy(registry);
    }

    @Async
    public void asyncUndeploy(Registry registry) {
        DeployLog logEntry = createLog(registry.getId(), "uninstall");
        StringBuilder logText = new StringBuilder();
        try {
            if ("ssh".equals(registry.getDeployMode())) {
                sshDeployService.undeploy(registry, line -> logText.append(line).append("\n"));
            } else if ("k8s".equals(registry.getDeployMode())) {
                k8sDeployService.undeploy(registry, line -> logText.append(line).append("\n"));
            }
            if (!isCancelled(registry.getId())) {
                registryMapper.updateStatus(registry.getId(), "not_deployed");
                finishLog(logEntry, "success", logText.toString());
            }
        } catch (Exception e) {
            if (!isCancelled(registry.getId())) {
                log.error("Undeploy failed for registry {}: {}", registry.getId(), e.getMessage(), e);
                logText.append("\nERROR: ").append(e.getMessage());
                registryMapper.updateStatus(registry.getId(), "failed");
                finishLog(logEntry, "failed", logText.toString());
            }
        } finally {
            cancelledSet.remove(registry.getId());
        }
    }

    @Override
    public void start(Long registryId) {
        executeLifecycleAction(registryId, "start");
    }

    @Override
    public void stop(Long registryId) {
        executeLifecycleAction(registryId, "stop");
    }

    @Override
    public void restart(Long registryId) {
        executeLifecycleAction(registryId, "restart");
    }

    @Override
    public void upgrade(Long registryId) {
        Registry registry = getRegistry(registryId);
        registryMapper.updateStatus(registryId, "deploying");
        applicationContext.getBean(RegistryDeployServiceImpl.class).asyncUpgrade(registry);
    }

    @Async
    public void asyncUpgrade(Registry registry) {
        DeployLog logEntry = createLog(registry.getId(), "upgrade");
        StringBuilder logText = new StringBuilder();
        try {
            if ("ssh".equals(registry.getDeployMode())) {
                sshDeployService.upgrade(registry, line -> logText.append(line).append("\n"));
            } else if ("k8s".equals(registry.getDeployMode())) {
                k8sDeployService.upgrade(registry, line -> logText.append(line).append("\n"));
            }
            registryMapper.updateStatus(registry.getId(), "running");
            finishLog(logEntry, "success", logText.toString());
        } catch (Exception e) {
            log.error("Upgrade failed for registry {}: {}", registry.getId(), e.getMessage(), e);
            logText.append("\nERROR: ").append(e.getMessage());
            registryMapper.updateStatus(registry.getId(), "failed");
            finishLog(logEntry, "failed", logText.toString());
        }
    }

    private void executeLifecycleAction(Long registryId, String action) {
        Registry registry = getRegistry(registryId);
        if (!"ssh".equals(registry.getDeployMode())) {
            throw new IllegalArgumentException(action + " is only supported for SSH-deployed registries. Use K8s scaling for K8s deployments.");
        }
        applicationContext.getBean(RegistryDeployServiceImpl.class).asyncLifecycle(registry, action);
    }

    @Async
    public void asyncLifecycle(Registry registry, String action) {
        DeployLog logEntry = createLog(registry.getId(), action);
        StringBuilder logText = new StringBuilder();
        try {
            sshDeployService.lifecycle(registry, action, line -> logText.append(line).append("\n"));
            String newStatus = "stop".equals(action) ? "stopped" : "running";
            registryMapper.updateStatus(registry.getId(), newStatus);
            finishLog(logEntry, "success", logText.toString());
        } catch (Exception e) {
            log.error("{} failed for registry {}: {}", action, registry.getId(), e.getMessage(), e);
            logText.append("\nERROR: ").append(e.getMessage());
            finishLog(logEntry, "failed", logText.toString());
        }
    }

    @Override
    public void cancelDeploy(Long registryId) {
        Registry registry = getRegistry(registryId);
        String status = registry.getStatus();
        if (!"deploying".equals(status) && !"uninstalling".equals(status)) {
            throw new IllegalArgumentException("Registry is not in a deploy/uninstall state, current: " + status);
        }
        cancelledSet.add(registryId);
        // Revert status: deploying→failed, uninstalling→previous running state
        String revertStatus = "uninstalling".equals(status) ? "running" : "failed";
        registryMapper.updateStatus(registryId, revertStatus);
        // Mark the latest running log as cancelled
        List<DeployLog> logs = deployLogMapper.findByRegistryId(registryId);
        for (DeployLog dl : logs) {
            if ("running".equals(dl.getStatus())) {
                dl.setStatus("failed");
                dl.setLogText((dl.getLogText() != null ? dl.getLogText() : "") + "\n[CANCELLED] Deploy cancelled by user");
                dl.setFinishedAt(LocalDateTime.now());
                deployLogMapper.update(dl);
                break;
            }
        }
        log.info("Deploy cancelled for registry {}", registryId);
    }

    public boolean isCancelled(Long registryId) {
        return cancelledSet.contains(registryId);
    }

    @Override
    public List<DeployLog> getDeployLogs(Long registryId) {
        return deployLogMapper.findByRegistryId(registryId);
    }

    private Registry getRegistry(Long id) {
        Registry registry = registryMapper.findById(id);
        if (registry == null) {
            throw new IllegalArgumentException("Registry not found: " + id);
        }
        return registry;
    }

    private DeployLog createLog(Long registryId, String action) {
        DeployLog logEntry = new DeployLog();
        logEntry.setRegistryId(registryId);
        logEntry.setAction(action);
        logEntry.setStatus("running");
        logEntry.setStartedAt(LocalDateTime.now());
        deployLogMapper.insert(logEntry);
        return logEntry;
    }

    private void finishLog(DeployLog logEntry, String status, String logText) {
        logEntry.setStatus(status);
        logEntry.setLogText(logText);
        logEntry.setFinishedAt(LocalDateTime.now());
        deployLogMapper.update(logEntry);
    }
}
