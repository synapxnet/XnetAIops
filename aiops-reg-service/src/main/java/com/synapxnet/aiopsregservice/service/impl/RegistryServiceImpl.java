package com.synapxnet.aiopsregservice.service.impl;

import com.synapxnet.aiopsregservice.entity.Registry;
import com.synapxnet.aiopsregservice.mapper.RegistryMapper;
import com.synapxnet.aiopsregservice.service.RegistryApiService;
import com.synapxnet.aiopsregservice.service.RegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RegistryServiceImpl implements RegistryService {

    private static final Logger log = LoggerFactory.getLogger(RegistryServiceImpl.class);

    private final RegistryMapper registryMapper;
    private final RegistryApiService registryApiService;

    public RegistryServiceImpl(RegistryMapper registryMapper, RegistryApiService registryApiService) {
        this.registryMapper = registryMapper;
        this.registryApiService = registryApiService;
    }

    @Override
    public List<Registry> listAll() {
        return registryMapper.findAll();
    }

    @Override
    public Registry getById(Long id) {
        Registry registry = registryMapper.findById(id);
        if (registry == null) {
            throw new IllegalArgumentException("Registry not found: " + id);
        }
        return registry;
    }

    @Override
    public Registry create(Registry registry) {
        registry.setUid(UUID.randomUUID().toString());
        registry.setStatus("not_deployed");
        registry.setCreatedAt(LocalDateTime.now());
        registry.setUpdatedAt(LocalDateTime.now());
        registryMapper.insert(registry);
        return registry;
    }

    @Override
    public Registry update(Registry registry) {
        Registry existing = getById(registry.getId());
        existing.setRegistryName(registry.getRegistryName());
        existing.setDescription(registry.getDescription());
        existing.setVersion(registry.getVersion());
        existing.setEndpoint(registry.getEndpoint());
        existing.setApiUrl(registry.getApiUrl());
        existing.setAdminUser(registry.getAdminUser());
        existing.setEncryptedAdminPassword(registry.getEncryptedAdminPassword());
        existing.setUseSsl(registry.getUseSsl());
        existing.setCertPem(registry.getCertPem());
        existing.setHelmValues(registry.getHelmValues());
        existing.setUpdatedAt(LocalDateTime.now());
        registryMapper.update(existing);
        return existing;
    }

    @Override
    public void delete(Long id) {
        Registry registry = getById(id);
        if ("running".equals(registry.getStatus()) || "deploying".equals(registry.getStatus())) {
            throw new IllegalArgumentException("Cannot delete registry in " + registry.getStatus() + " state. Please stop/undeploy first.");
        }
        registryMapper.deleteById(id);
    }

    @Override
    public String getStatus(Long id) {
        Registry registry = getById(id);
        if (!"running".equals(registry.getStatus()) && !"stopped".equals(registry.getStatus())) {
            return registry.getStatus();
        }
        // Probe the registry API to check if it's actually reachable
        try {
            boolean reachable = registryApiService.probe(registry);
            String newStatus = reachable ? "running" : "stopped";
            if (!newStatus.equals(registry.getStatus())) {
                registryMapper.updateStatus(id, newStatus);
            }
            return newStatus;
        } catch (Exception e) {
            log.warn("Failed to probe registry {}: {}", id, e.getMessage());
            return registry.getStatus();
        }
    }
}
