package com.synapxnet.aiopsclmservice.service;

import com.synapxnet.aiopsclmservice.entity.Cluster;
import com.synapxnet.aiopsclmservice.entity.ClusterVariable;

import java.util.List;
import java.util.Map;

public interface ClusterService {

    List<Cluster> listAll();

    Cluster getById(Long id);

    Cluster create(Cluster cluster);

    Cluster update(Cluster cluster);

    void delete(Long id);

    Map<String, Object> getOverview(Long id);

    List<ClusterVariable> getVariables(Long clusterId);

    void saveVariable(ClusterVariable variable);

    void deleteVariable(Long variableId);
}
