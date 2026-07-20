package com.synapxnet.aiopsclmservice.entity;

import lombok.Data;

/**
 * Redis 部署配置 DTO
 */
@Data
public class RedisDeployConfig {

    /** Redis版本 (7.2/7.0/6.2) */
    private String redisVersion;

    /** Redis端口 */
    private Integer redisPort = 6379;

    /** Redis密码 */
    private String redisPassword;

    /** 最大内存 (MB) */
    private Integer maxMemory = 1024;

    /** 内存淘汰策略 */
    private String maxMemoryPolicy = "noeviction";

    /** 持久化模式: none/rdb/aof/both */
    private String persistenceMode = "rdb";

    /** 数据目录 */
    private String dataDir = "/var/lib/redis";

    /** 部署模式: standalone/sentinel/cluster */
    private String deployMode = "standalone";

    /** 实例角色: master/slave/sentinel */
    private String role = "master";

    /** 主节点地址 (slave/sentinel配置时使用) */
    private String masterHost;

    /** 主节点端口 */
    private Integer masterPort;

    /** 主节点密码 */
    private String masterPassword;

    /** Sentinel 监控名称 */
    private String sentinelMasterName = "mymaster";

    /** Sentinel quorum */
    private Integer sentinelQuorum = 2;
}
