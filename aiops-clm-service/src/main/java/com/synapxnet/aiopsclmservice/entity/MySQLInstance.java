package com.synapxnet.aiopsclmservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * MySQL 实例实体类
 * 存储 MySQL 部署节点的配置和状态信息
 */
@Data
public class MySQLInstance {

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

    // ==================== MySQL配置 ====================

    /** MySQL端口 */
    private Integer mysqlPort;

    /** MySQL版本 */
    private String mysqlVersion;

    /** 数据目录 */
    private String dataDir;

    /** 字符集 */
    private String charset;

    /** InnoDB缓冲池大小 (MB) */
    private Integer innodbBufferPoolSize;

    /** 最大连接数 */
    private Integer maxConnections;

    /** root密码(AES加密存储) */
    private String encryptedRootPassword;

    // ==================== 主从复制配置 ====================

    /** 实例角色: standalone/master/slave */
    private String role;

    /** 主节点ID (slave节点使用) */
    private Long masterInstanceId;

    /** server-id (主从复制用) */
    private Integer serverId;

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
