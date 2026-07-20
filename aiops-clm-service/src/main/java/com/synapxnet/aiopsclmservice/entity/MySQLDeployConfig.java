package com.synapxnet.aiopsclmservice.entity;

import lombok.Data;

/**
 * MySQL 部署配置 DTO
 * 用于部署时传递参数
 */
@Data
public class MySQLDeployConfig {

    /** MySQL版本 (8.0/5.7) */
    private String mysqlVersion;

    /** MySQL端口 */
    private Integer mysqlPort = 3306;

    /** 数据目录 */
    private String dataDir = "/var/lib/mysql";

    /** root密码 */
    private String rootPassword;

    /** 字符集 */
    private String charset = "utf8mb4";

    /** InnoDB缓冲池大小 (MB) */
    private Integer innodbBufferPoolSize = 1024;

    /** 最大连接数 */
    private Integer maxConnections = 500;

    /** 实例角色: standalone/master/slave */
    private String role = "standalone";

    /** server-id */
    private Integer serverId = 1;

    /** 主节点地址 (slave配置时使用) */
    private String masterHost;

    /** 主节点端口 (slave配置时使用) */
    private Integer masterPort;

    /** 复制用户名 */
    private String replUser = "repl";

    /** 复制用户密码 */
    private String replPassword;
}
