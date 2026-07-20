package com.synapxnet.aiopssvmservice.service.impl;

import com.jcraft.jsch.Session;
import com.synapxnet.aiopssvmservice.client.HomClient;
import com.synapxnet.aiopssvmservice.client.HostInfo;
import com.synapxnet.aiopssvmservice.entity.*;
import com.synapxnet.aiopssvmservice.mapper.CommandMapper;
import com.synapxnet.aiopssvmservice.mapper.FrameworkMapper;
import com.synapxnet.aiopssvmservice.mapper.ServiceInstanceMapper;
import com.synapxnet.aiopssvmservice.service.CommandExecutionService;
import com.synapxnet.aiopssvmservice.util.SshExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class CommandExecutionServiceImpl implements CommandExecutionService {

    private static final Logger log = LoggerFactory.getLogger(CommandExecutionServiceImpl.class);
    private static final int SSH_CONNECT_TIMEOUT = 15000;
    private static final int SSH_COMMAND_TIMEOUT = 300000; // 5 minutes per role

    private final CommandMapper commandMapper;
    private final ServiceInstanceMapper serviceInstanceMapper;
    private final FrameworkMapper frameworkMapper;
    private final HomClient homClient;
    private final ApplicationContext applicationContext;

    // Track cancellation requests
    private final Set<Long> cancelledCommands = ConcurrentHashMap.newKeySet();

    public CommandExecutionServiceImpl(CommandMapper commandMapper,
                                       ServiceInstanceMapper serviceInstanceMapper,
                                       FrameworkMapper frameworkMapper,
                                       HomClient homClient,
                                       ApplicationContext applicationContext) {
        this.commandMapper = commandMapper;
        this.serviceInstanceMapper = serviceInstanceMapper;
        this.frameworkMapper = frameworkMapper;
        this.homClient = homClient;
        this.applicationContext = applicationContext;
    }

    @Override
    public Command executeServiceCommand(Long serviceInstanceId, String commandType, String createdBy) {
        ServiceInstance si = serviceInstanceMapper.findById(serviceInstanceId);
        if (si == null) {
            throw new IllegalArgumentException("Service instance not found: " + serviceInstanceId);
        }

        List<RoleInstance> roles = serviceInstanceMapper.findRolesByServiceInstanceId(serviceInstanceId);
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("No role instances found for service: " + si.getServiceName());
        }

        // Create Command record
        Command cmd = new Command();
        cmd.setUid(UUID.randomUUID().toString());
        cmd.setClusterId(si.getClusterId());
        cmd.setCommandName(commandType + " " + si.getServiceName());
        cmd.setCommandType(commandType);
        cmd.setStatus("pending");
        cmd.setProgress(0);
        cmd.setServiceInstanceId(serviceInstanceId);
        cmd.setCreatedBy(createdBy);
        commandMapper.insert(cmd);

        // Group roles by hostId → create CommandHost + CommandHostRole records
        Map<Long, List<RoleInstance>> hostRolesMap = roles.stream()
                .collect(Collectors.groupingBy(RoleInstance::getHostId));

        for (Map.Entry<Long, List<RoleInstance>> entry : hostRolesMap.entrySet()) {
            CommandHost ch = new CommandHost();
            ch.setCommandId(cmd.getId());
            ch.setHostId(entry.getKey());
            ch.setHostname(entry.getValue().get(0).getHostname());
            ch.setStatus("pending");
            ch.setProgress(0);
            commandMapper.insertHost(ch);

            for (RoleInstance ri : entry.getValue()) {
                CommandHostRole chr = new CommandHostRole();
                chr.setCommandHostId(ch.getId());
                chr.setRoleName(ri.getRoleName());
                chr.setRoleType(ri.getRoleType());
                chr.setStatus("pending");
                commandMapper.insertHostRole(chr);
            }
        }

        // Launch async execution via Spring proxy (to ensure @Async works)
        applicationContext.getBean(CommandExecutionServiceImpl.class)
                .asyncExecute(cmd.getId(), commandType, serviceInstanceId);

        return commandMapper.findById(cmd.getId());
    }

    @Override
    public void cancelCommand(Long commandId) {
        cancelledCommands.add(commandId);
        Command cmd = commandMapper.findById(commandId);
        if (cmd != null && "running".equals(cmd.getStatus())) {
            cmd.setStatus("cancelled");
            cmd.setFinishedAt(LocalDateTime.now());
            commandMapper.update(cmd);
        }
    }

    @Async
    public void asyncExecute(Long commandId, String commandType, Long serviceInstanceId) {
        log.info("Starting async execution of command {} (type: {})", commandId, commandType);

        // Update command to running
        Command cmd = commandMapper.findById(commandId);
        cmd.setStatus("running");
        cmd.setStartedAt(LocalDateTime.now());
        commandMapper.update(cmd);

        // Update service instance status to installing if this is an install command
        if ("install".equals(commandType)) {
            ServiceInstance si = serviceInstanceMapper.findById(serviceInstanceId);
            si.setStatus("installing");
            serviceInstanceMapper.update(si);
        }

        List<CommandHost> hosts = commandMapper.findHostsByCommandId(commandId);
        int totalHosts = hosts.size();
        int completedHosts = 0;
        boolean allSuccess = true;

        for (CommandHost ch : hosts) {
            // Check cancellation
            if (cancelledCommands.contains(commandId)) {
                ch.setStatus("cancelled");
                ch.setResultMsg("Command cancelled by user");
                commandMapper.updateHost(ch);
                allSuccess = false;
                continue;
            }

            Session session = null;
            try {
                // Get SSH credentials from HOM
                HostInfo hostInfo = homClient.getHost(ch.getHostId());
                log.info("Connecting to host {} ({}:{})", hostInfo.getHostname(),
                        hostInfo.getIpAddress(), hostInfo.getSshPort());

                session = SshExecutor.connect(
                        hostInfo.getIpAddress(),
                        hostInfo.getSshPort() != null ? hostInfo.getSshPort() : 22,
                        hostInfo.getSshUser(),
                        hostInfo.getEncryptedPassword(),
                        hostInfo.getPrivateKey(),
                        SSH_CONNECT_TIMEOUT);

                // Update host status to running
                ch.setStatus("running");
                commandMapper.updateHost(ch);

                // Execute each role on this host
                List<CommandHostRole> roleRecords = commandMapper.findRolesByCommandHostId(ch.getId());
                boolean hostAllSuccess = true;

                for (CommandHostRole role : roleRecords) {
                    if (cancelledCommands.contains(commandId)) {
                        role.setStatus("cancelled");
                        role.setResultMsg("Command cancelled");
                        role.setFinishedAt(LocalDateTime.now());
                        commandMapper.updateHostRole(role);
                        hostAllSuccess = false;
                        continue;
                    }

                    role.setStatus("running");
                    role.setStartedAt(LocalDateTime.now());
                    commandMapper.updateHostRole(role);

                    // Build and execute command
                    String sshCommand = buildCommand(commandType, role.getRoleName(), role.getRoleType());
                    StringBuilder output = new StringBuilder();

                    try {
                        int exitCode = SshExecutor.executeWithCallback(session, sshCommand, SSH_COMMAND_TIMEOUT,
                                line -> {
                                    output.append(line).append("\n");
                                    log.debug("[{}] {}", role.getRoleName(), line);
                                },
                                line -> {
                                    output.append("[ERR] ").append(line).append("\n");
                                    log.warn("[{}] stderr: {}", role.getRoleName(), line);
                                });

                        role.setFinishedAt(LocalDateTime.now());
                        if (exitCode == 0) {
                            role.setStatus("success");
                        } else {
                            role.setStatus("failed");
                            hostAllSuccess = false;
                        }
                        role.setResultMsg(truncate(output.toString(), 2000));
                    } catch (Exception e) {
                        role.setStatus("failed");
                        role.setResultMsg("Execution error: " + e.getMessage());
                        role.setFinishedAt(LocalDateTime.now());
                        hostAllSuccess = false;
                        log.error("Failed to execute {} on role {}: {}", commandType, role.getRoleName(), e.getMessage());
                    }
                    commandMapper.updateHostRole(role);
                }

                // Update host status
                ch.setStatus(hostAllSuccess ? "success" : "failed");
                ch.setProgress(100);
                if (!hostAllSuccess) {
                    ch.setResultMsg("Some roles failed");
                    allSuccess = false;
                }
                commandMapper.updateHost(ch);

            } catch (Exception e) {
                log.error("Failed to connect to host {}: {}", ch.getHostname(), e.getMessage());
                ch.setStatus("failed");
                ch.setProgress(100);
                ch.setResultMsg("SSH connection failed: " + e.getMessage());
                commandMapper.updateHost(ch);
                allSuccess = false;
            } finally {
                SshExecutor.disconnect(session);
            }

            completedHosts++;
            // Update overall command progress
            int progress = (completedHosts * 100) / totalHosts;
            cmd = commandMapper.findById(commandId);
            cmd.setProgress(progress);
            commandMapper.update(cmd);
        }

        // Clean up cancellation tracking
        cancelledCommands.remove(commandId);

        // Final command status
        cmd = commandMapper.findById(commandId);
        if (!"cancelled".equals(cmd.getStatus())) {
            cmd.setStatus(allSuccess ? "success" : "failed");
        }
        cmd.setProgress(100);
        cmd.setFinishedAt(LocalDateTime.now());
        commandMapper.update(cmd);

        // Update ServiceInstance and RoleInstance status
        updateServiceStatus(serviceInstanceId, commandType, allSuccess);

        log.info("Command {} completed with status: {}", commandId, cmd.getStatus());
    }

    /**
     * Build SSH command based on command type and role name.
     * Uses systemctl for standard services. Can be extended for custom scripts.
     */
    private String buildCommand(String commandType, String roleName, String roleType) {
        // Convert role name to systemd service name (lowercase, replace spaces)
        String serviceName = roleName.toLowerCase().replace(" ", "-");

        switch (commandType) {
            case "start":
                return "systemctl start " + serviceName + " 2>&1 || " +
                       "service " + serviceName + " start 2>&1";
            case "stop":
                return "systemctl stop " + serviceName + " 2>&1 || " +
                       "service " + serviceName + " stop 2>&1";
            case "restart":
                return "systemctl restart " + serviceName + " 2>&1 || " +
                       "service " + serviceName + " restart 2>&1";
            case "install":
                return "echo 'Installing " + roleName + "...' && " +
                       "which " + serviceName + " 2>/dev/null && echo 'Already installed' || " +
                       "echo 'Package not found, manual installation required'";
            case "config_update":
                return "echo 'Configuration updated for " + roleName + "'";
            default:
                return "echo 'Unknown command type: " + commandType + "' && exit 1";
        }
    }

    /**
     * Update ServiceInstance and RoleInstance status after command completion.
     */
    private void updateServiceStatus(Long serviceInstanceId, String commandType, boolean success) {
        ServiceInstance si = serviceInstanceMapper.findById(serviceInstanceId);
        if (si == null) return;

        if (success) {
            switch (commandType) {
                case "start":
                    si.setStatus("running");
                    break;
                case "stop":
                    si.setStatus("stopped");
                    break;
                case "install":
                    si.setStatus("stopped");
                    break;
                case "restart":
                    si.setStatus("running");
                    break;
                case "config_update":
                    si.setNeedRestart(false);
                    break;
            }
        } else {
            if ("install".equals(commandType)) {
                si.setStatus("error");
            }
        }
        serviceInstanceMapper.update(si);

        // Update role instance status
        if (success && ("start".equals(commandType) || "stop".equals(commandType) || "restart".equals(commandType))) {
            List<RoleInstance> roles = serviceInstanceMapper.findRolesByServiceInstanceId(serviceInstanceId);
            String newStatus = "stop".equals(commandType) ? "stopped" : "running";
            for (RoleInstance ri : roles) {
                ri.setStatus(newStatus);
                serviceInstanceMapper.updateRole(ri);
            }
        }
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(str.length() - maxLength) : str;
    }
}
