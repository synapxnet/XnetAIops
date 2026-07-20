package com.synapxnet.aiopsclmservice.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Date;

/**
 * Jenkins Master 实体类
 * 用于存储Jenkins主节点的配置和状态信息
 */
@Data
public class JenkinsMaster {

    /** 主键ID */
    private Long id;

    /** 唯一标识符(UUID) */
    private String uid;

    /** Master名称 */
    private String name;

    // ==================== SSH连接配置 ====================

    /** 主机地址 */
    private String host;

    /** SSH端口 */
    private Integer port;

    /** SSH用户名 */
    private String username;

    /** SSH密码(AES加密存储) - 不返回给前端 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String encrypted_password;

    /** 操作系统类型: linux, macos, windows */
    private String os_type;

    // ==================== Jenkins配置 ====================

    /** Jenkins HTTP端口 */
    private Integer jenkins_port;

    /** Jenkins Home目录 */
    private String jenkins_home;

    /** Jenkins版本 */
    private String jenkins_version;

    /** Java版本 */
    private String java_version;

    /** JVM参数 */
    private String java_opts;

    // ==================== 管理员配置 ====================

    /** 管理员用户名 */
    private String admin_username;

    /** 管理员密码(AES加密存储) - 不返回给前端 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String encrypted_admin_password;

    // ==================== 凭证配置 ====================

    /**
     * 凭证配置(JSON格式)
     */
    private String credentials_config;

    // ==================== 状态信息 ====================

    /** 状态: pending, deploying, deployed, failed, running, stopped */
    private String status;

    /** Jenkins初始密码(部署后获取) */
    private String initial_password;

    /** 部署日志 */
    private String deploy_log;

    /** 最后心跳时间 */
    private Date last_heartbeat;

    // ==================== 资源配置 ====================

    /** 地域 */
    private String region;

    /** CPU核数 */
    private Integer cpu_cores;

    /** 内存(GB) */
    private Integer ram_gb;

    /** 磁盘空间(GB) */
    private Integer disk_gb;

    // ==================== 关联字段 ====================

    /** 关联HOM主机ID(可选，如果从主机管理选择) */
    @JsonProperty("hostId")
    private Long host_id;

    /** 租户UID */
    private String tenant_uid;

    // ==================== 审计字段 ====================

    /** 描述 */
    private String description;

    /** 创建者 */
    private String created_by;

    /** 更新者 */
    private String updated_by;

    /** 创建时间 */
    private Date created_at;

    /** 更新时间 */
    private Date updated_at;

    // ==================== 非持久化字段(仅用于传输，只写不读) ====================

    /** SSH明文密码(仅用于创建/更新时传输，不存储到数据库) */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /** 管理员明文密码(仅用于创建/更新时传输，不存储到数据库) */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String admin_password;
}
