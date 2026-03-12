package com.synapxnet.aiopsregservice.service;

import com.synapxnet.aiopsregservice.entity.Registry;

import java.util.List;

public interface RegistryService {
    List<Registry> listAll();
    Registry getById(Long id);
    Registry create(Registry registry);
    Registry update(Registry registry);
    void delete(Long id);
    String getStatus(Long id);
}
