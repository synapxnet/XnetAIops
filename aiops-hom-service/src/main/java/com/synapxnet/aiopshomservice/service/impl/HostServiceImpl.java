package com.synapxnet.aiopshomservice.service.impl;

import com.synapxnet.aiopshomservice.entity.Host;
import com.synapxnet.aiopshomservice.entity.Rack;
import com.synapxnet.aiopshomservice.mapper.HostMapper;
import com.synapxnet.aiopshomservice.service.HostService;
import com.synapxnet.aiopshomservice.service.SshService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class HostServiceImpl implements HostService {

    private final HostMapper hostMapper;
    private final SshService sshService;

    public HostServiceImpl(HostMapper hostMapper, SshService sshService) {
        this.hostMapper = hostMapper;
        this.sshService = sshService;
    }

    @Override
    public List<Host> listAll() {
        return hostMapper.findAll();
    }

    @Override
    public List<Host> listByClusterId(Long clusterId) {
        return hostMapper.findByClusterId(clusterId);
    }

    @Override
    public Host getById(Long id) {
        Host host = hostMapper.findById(id);
        if (host == null) {
            throw new IllegalArgumentException("Host not found: " + id);
        }
        return host;
    }

    @Override
    public Host create(Host host) {
        host.setUid(UUID.randomUUID().toString());
        if (host.getStatus() == null) {
            host.setStatus("unknown");
        }
        if (host.getSshPort() == null) {
            host.setSshPort(22);
        }
        if (host.getSshUser() == null) {
            host.setSshUser("root");
        }
        if (host.getAuthType() == null) {
            host.setAuthType("password");
        }
        hostMapper.insert(host);
        return host;
    }

    @Override
    public Host update(Host host) {
        hostMapper.update(host);
        return hostMapper.findById(host.getId());
    }

    @Override
    public void delete(Long id) {
        hostMapper.deleteById(id);
    }

    @Override
    public Map<String, Object> testConnection(String host, int port, String user, String password) {
        return sshService.testConnection(host, port, user, password);
    }

    @Override
    public List<Rack> listRacks(Long clusterId) {
        return hostMapper.findRacksByClusterId(clusterId);
    }

    @Override
    public Rack createRack(Rack rack) {
        hostMapper.insertRack(rack);
        return rack;
    }

    @Override
    public void deleteRack(Long id) {
        hostMapper.deleteRack(id);
    }
}
