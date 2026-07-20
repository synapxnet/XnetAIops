package com.synapxnet.aiopsclmservice.service.impl;

import com.synapxnet.aiopsclmservice.entity.Cluster;
import com.synapxnet.aiopsclmservice.entity.ClusterVariable;
import com.synapxnet.aiopsclmservice.mapper.ClusterMapper;
import com.synapxnet.aiopsclmservice.service.ClusterService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ClusterServiceImpl implements ClusterService {

    private final ClusterMapper clusterMapper;

    public ClusterServiceImpl(ClusterMapper clusterMapper) {
        this.clusterMapper = clusterMapper;
    }

    @Override
    public List<Cluster> listAll() {
        return clusterMapper.findAll();
    }

    @Override
    public Cluster getById(Long id) {
        Cluster cluster = clusterMapper.findById(id);
        if (cluster == null) {
            throw new IllegalArgumentException("Cluster not found: " + id);
        }
        return cluster;
    }

    @Override
    public Cluster create(Cluster cluster) {
        cluster.setUid(UUID.randomUUID().toString());
        if (cluster.getStatus() == null) {
            cluster.setStatus("inactive");
        }
        if (cluster.getClusterType() == null) {
            cluster.setClusterType("hadoop");
        }
        clusterMapper.insert(cluster);
        return cluster;
    }

    @Override
    public Cluster update(Cluster cluster) {
        clusterMapper.update(cluster);
        return clusterMapper.findById(cluster.getId());
    }

    @Override
    public void delete(Long id) {
        clusterMapper.deleteById(id);
    }

    @Override
    public Map<String, Object> getOverview(Long id) {
        Cluster cluster = getById(id);
        Map<String, Object> overview = new HashMap<>();
        overview.put("cluster", cluster);
        overview.put("totalHosts", cluster.getTotalHosts());
        overview.put("runningServices", cluster.getRunningServices());
        return overview;
    }

    @Override
    public List<ClusterVariable> getVariables(Long clusterId) {
        return clusterMapper.findVariablesByClusterId(clusterId);
    }

    @Override
    public void saveVariable(ClusterVariable variable) {
        clusterMapper.upsertVariable(variable);
    }

    @Override
    public void deleteVariable(Long variableId) {
        clusterMapper.deleteVariable(variableId);
    }
}
