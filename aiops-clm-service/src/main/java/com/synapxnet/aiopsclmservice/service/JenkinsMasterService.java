package com.synapxnet.aiopsclmservice.service;

import com.synapxnet.aiopsclmservice.client.HostInfo;
import com.synapxnet.aiopsclmservice.entity.JenkinsMaster;
import com.synapxnet.aiopsclmservice.entity.JenkinsMasterDeployConfig;

import java.util.List;
import java.util.Map;

/**
 * Jenkins Master 服务接口
 */
public interface JenkinsMasterService {

    // ==================== CRUD操作 ====================
    JenkinsMaster createMaster(JenkinsMaster master);

    JenkinsMaster updateMaster(Long id, JenkinsMaster master);

    void deleteMaster(Long id);

    JenkinsMaster getMasterById(Long id);

    JenkinsMaster getMasterByUid(String uid);

    List<JenkinsMaster> getAllMasters();

    List<JenkinsMaster> getMastersByStatus(String status);

    List<JenkinsMaster> getDeployedMasters();

    // ==================== HOM主机集成 ====================
    List<HostInfo> getHomHosts();

    // ==================== SSH连接 ====================
    Map<String, Object> testConnection(JenkinsMaster master);

    // ==================== 部署操作 ====================
    Map<String, Object> deployMaster(Long masterId, JenkinsMasterDeployConfig config);

    String getDeployScript(String osType, JenkinsMasterDeployConfig config);

    // ==================== 状态管理 ====================
    Map<String, Object> checkMasterStatus(Long masterId);

    Map<String, Object> startJenkins(Long masterId);

    Map<String, Object> stopJenkins(Long masterId);

    Map<String, Object> restartJenkins(Long masterId);

    String getInitialPassword(Long masterId);

    // ==================== 凭证管理 ====================
    Map<String, Object> configureCredentials(Long masterId, JenkinsMasterDeployConfig config);

    // ==================== Node管理 ====================
    Map<String, Object> createNodeOnMaster(Long masterId, String nodeName, String workDir, String labels);

    String getNodeSecret(Long masterId, String nodeName);

    // ==================== 卸载操作 ====================
    Map<String, Object> uninstallJenkins(Long masterId);
}
