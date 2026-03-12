package com.synapxnet.aiopsclmservice.service;

import com.synapxnet.aiopsclmservice.entity.RedisDeployConfig;
import com.synapxnet.aiopsclmservice.entity.RedisInstance;

import java.util.List;
import java.util.Map;

/**
 * Redis 实例管理服务接口
 */
public interface RedisInstanceService {

    // ==================== CRUD ====================

    List<RedisInstance> listAll();

    RedisInstance getById(Long id);

    List<RedisInstance> listByClusterId(Long clusterId);

    RedisInstance create(RedisInstance instance);

    RedisInstance update(RedisInstance instance);

    void delete(Long id);

    // ==================== 部署操作 ====================

    Map<String, Object> testConnection(RedisInstance instance);

    Map<String, Object> deploy(Long id, RedisDeployConfig config);

    String getDeployScript(RedisDeployConfig config);

    // ==================== 状态管理 ====================

    Map<String, Object> checkStatus(Long id);

    Map<String, Object> startRedis(Long id);

    Map<String, Object> stopRedis(Long id);

    Map<String, Object> restartRedis(Long id);
}
