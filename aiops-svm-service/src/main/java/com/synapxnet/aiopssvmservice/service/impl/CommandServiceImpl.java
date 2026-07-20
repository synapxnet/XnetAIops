package com.synapxnet.aiopssvmservice.service.impl;

import com.synapxnet.aiopssvmservice.entity.Command;
import com.synapxnet.aiopssvmservice.entity.CommandHost;
import com.synapxnet.aiopssvmservice.entity.CommandHostRole;
import com.synapxnet.aiopssvmservice.mapper.CommandMapper;
import com.synapxnet.aiopssvmservice.service.CommandService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class CommandServiceImpl implements CommandService {

    private final CommandMapper commandMapper;

    public CommandServiceImpl(CommandMapper commandMapper) {
        this.commandMapper = commandMapper;
    }

    @Override
    public List<Command> listByClusterId(Long clusterId) {
        return commandMapper.findByClusterId(clusterId);
    }

    @Override
    public List<Command> listAll() {
        return commandMapper.findAll();
    }

    @Override
    public Map<String, Object> getDetail(Long id) {
        Command command = commandMapper.findById(id);
        if (command == null) {
            throw new IllegalArgumentException("Command not found: " + id);
        }
        List<CommandHost> hosts = commandMapper.findHostsByCommandId(id);

        // Enrich each host with its role execution details
        List<Map<String, Object>> enrichedHosts = new ArrayList<>();
        for (CommandHost host : hosts) {
            Map<String, Object> hostMap = new HashMap<>();
            hostMap.put("id", host.getId());
            hostMap.put("commandId", host.getCommandId());
            hostMap.put("hostId", host.getHostId());
            hostMap.put("hostname", host.getHostname());
            hostMap.put("status", host.getStatus());
            hostMap.put("progress", host.getProgress());
            hostMap.put("resultMsg", host.getResultMsg());
            hostMap.put("roles", commandMapper.findRolesByCommandHostId(host.getId()));
            enrichedHosts.add(hostMap);
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("command", command);
        detail.put("hosts", enrichedHosts);
        return detail;
    }

    @Override
    public Command create(Command command) {
        command.setUid(UUID.randomUUID().toString());
        if (command.getStatus() == null) {
            command.setStatus("pending");
        }
        if (command.getProgress() == null) {
            command.setProgress(0);
        }
        commandMapper.insert(command);
        return command;
    }

    @Override
    public Command updateStatus(Long id, String status, Integer progress) {
        Command command = commandMapper.findById(id);
        if (command == null) {
            throw new IllegalArgumentException("Command not found: " + id);
        }
        command.setStatus(status);
        if (progress != null) {
            command.setProgress(progress);
        }
        if ("running".equals(status) && command.getStartedAt() == null) {
            command.setStartedAt(LocalDateTime.now());
        }
        if ("success".equals(status) || "failed".equals(status) || "cancelled".equals(status)) {
            command.setFinishedAt(LocalDateTime.now());
        }
        commandMapper.update(command);
        return commandMapper.findById(id);
    }
}
