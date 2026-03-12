package com.synapxnet.aiopshomservice.service;

import com.synapxnet.aiopshomservice.entity.Host;
import com.synapxnet.aiopshomservice.entity.Rack;

import java.util.List;
import java.util.Map;

public interface HostService {

    List<Host> listAll();

    List<Host> listByClusterId(Long clusterId);

    Host getById(Long id);

    Host create(Host host);

    Host update(Host host);

    void delete(Long id);

    Map<String, Object> testConnection(String host, int port, String user, String password);

    List<Rack> listRacks(Long clusterId);

    Rack createRack(Rack rack);

    void deleteRack(Long id);
}
