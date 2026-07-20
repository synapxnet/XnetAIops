package com.synapxnet.aiopsclmservice.service;

import com.synapxnet.aiopsclmservice.entity.HadoopCluster;
import com.synapxnet.aiopsclmservice.entity.HadoopDeployConfig;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hadoop 集群服务接口
 */
public interface HadoopClusterService {

    // ==================== CRUD 操作 ====================
    List<HadoopCluster> getAll();

    List<HadoopCluster> getMasters();

    List<HadoopCluster> getNodes();

    Optional<HadoopCluster> getById(Long id);

    Optional<HadoopCluster> getByUid(String uid);

    HadoopCluster create(HadoopCluster cluster, String userId);

    HadoopCluster update(Long id, HadoopCluster cluster, String userId);

    boolean delete(Long id);

    // ==================== 部署操作 ====================
    Map<String, Object> testConnection(HadoopCluster cluster);

    Map<String, Object> deploy(Long id, HadoopDeployConfig config);

    String previewScript(String osType, String nodeType, HadoopDeployConfig config);

    // ==================== 状态操作 ====================
    Map<String, Object> checkStatus(Long id);

    Map<String, Object> startServices(Long id);

    Map<String, Object> stopServices(Long id);

    Map<String, Object> restartServices(Long id);

    // ==================== 集群健康 ====================
    Map<String, Object> getClusterHealth(Long masterId);

    Map<String, Object> getHdfsStatus(Long masterId);

    Map<String, Object> getYarnStatus(Long masterId);
}
