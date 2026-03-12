package com.synapxnet.aiopsclmservice.service;

import com.synapxnet.aiopsclmservice.entity.JenkinsNode;
import com.synapxnet.aiopsclmservice.entity.JenkinsNodeDeployConfig;
import java.util.List;
import java.util.Map;

public interface JenkinsNodeService {
    // CRUD操作
    JenkinsNode createNode(JenkinsNode node);

    JenkinsNode updateNode(Long id, JenkinsNode node);

    void deleteNode(Long id);

    JenkinsNode getNodeById(Long id);

    JenkinsNode getNodeByUid(String uid);

    List<JenkinsNode> getAllNodes();

    List<JenkinsNode> getNodesByStatus(String status);

    // SSH连接测试
    Map<String, Object> testConnection(JenkinsNode node);

    // 部署操作
    Map<String, Object> deployNode(Long nodeId, JenkinsNodeDeployConfig config);

    String getDeployScript(String osType, JenkinsNodeDeployConfig config);

    // 状态管理
    Map<String, Object> checkNodeStatus(Long nodeId);

    Map<String, Object> stopAgent(Long nodeId);

    Map<String, Object> startAgent(Long nodeId);

    Map<String, Object> uninstallAgent(Long nodeId);
}
