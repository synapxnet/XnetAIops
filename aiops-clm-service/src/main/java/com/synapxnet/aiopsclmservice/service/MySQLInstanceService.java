package com.synapxnet.aiopsclmservice.service;

import com.synapxnet.aiopsclmservice.entity.MySQLDeployConfig;
import com.synapxnet.aiopsclmservice.entity.MySQLInstance;

import java.util.List;
import java.util.Map;

/**
 * MySQL 实例管理服务接口
 */
public interface MySQLInstanceService {

    // ==================== CRUD ====================

    List<MySQLInstance> listAll();

    MySQLInstance getById(Long id);

    List<MySQLInstance> listByClusterId(Long clusterId);

    MySQLInstance create(MySQLInstance instance);

    MySQLInstance update(MySQLInstance instance);

    void delete(Long id);

    // ==================== 部署操作 ====================

    /** 测试SSH连接 */
    Map<String, Object> testConnection(MySQLInstance instance);

    /** 部署MySQL */
    Map<String, Object> deploy(Long id, MySQLDeployConfig config);

    /** 获取部署脚本预览 */
    String getDeployScript(MySQLDeployConfig config);

    // ==================== 状态管理 ====================

    Map<String, Object> checkStatus(Long id);

    Map<String, Object> startMySQL(Long id);

    Map<String, Object> stopMySQL(Long id);

    Map<String, Object> restartMySQL(Long id);
}
