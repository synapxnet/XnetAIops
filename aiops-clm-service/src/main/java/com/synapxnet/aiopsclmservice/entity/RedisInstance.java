package com.synapxnet.aiopsclmservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Redis 实例实体类
 * 存储 Redis 部署节点的配置和状态信息
 */
@Data
public class RedisInstance {

    /** 主键ID */
    private Long id;

    /** 唯一标识符(UUID) */
    private String uid;

    /** 实例名称 */
    private String instanceName;

    /** 关联的集群ID */
    private Long clusterId;

    // ==================== SSH连接配置 ====================

    /** 主机地址 */
    private String host;

    /** SSH端口 */
    private Integer sshPort;

    /** SSH用户名 */
    private String sshUser;

    /** 认证方式: password/privateKey */
    private String authType;

    /** SSH密码(AES加密存储) */
    private String encryptedPassword;

    /** SSH私钥(AES加密存储) */
    private String encryptedPrivateKey;

    // ==================== Redis配置 ====================

    /** Redis端口 */
    private Integer redisPort;

    /** Redis版本 */
    private String redisVersion;

    /** Redis密码(AES加密存储) */
    private String encryptedRedisPassword;

    /** 最大内存 (MB) */
    private Integer maxMemory;

    /** 内存淘汰策略: noeviction/allkeys-lru/volatile-lru/allkeys-random/volatile-ttl */
    private String maxMemoryPolicy;

    /** 持久化模式: none/rdb/aof/both */
    private String persistenceMode;

    /** 数据目录 */
    private String dataDir;

    // ==================== 集群/哨兵配置 ====================

    /** 部署模式: standalone/sentinel/cluster */
    private String deployMode;

    /** 实例角色: master/slave/sentinel */
    private String role;

    /** 主节点ID */
    private Long masterInstanceId;

    /** 集群总线端口 (cluster模式, 默认 redisPort+10000) */
    private Integer clusterBusPort;

    // ==================== 状态信息 ====================

    /** 状态: pending/deploying/deployed/running/stopped/failed */
    private String status;

    /** 部署日志 */
    private String deployLog;

    /** 最后心跳时间 */
    private LocalDateTime lastHeartbeat;

    // ==================== 审计字段 ====================

    /** 描述 */
    private String description;

    /** 创建者 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
