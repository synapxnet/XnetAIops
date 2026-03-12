package com.synapxnet.aiopsclmservice.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

/**
 * Hadoop 部署配置实体类
 * 用于接收前端传递的部署参数
 */
@Data
public class HadoopDeployConfig {

    /** Hadoop版本 */
    private String hadoopVersion;

    /** 操作系统类型 */
    private String osType;

    /** 部署模式 (standard/ha) */
    private String deployMode;

    /** Java版本 (8/11/17/21) */
    private String javaVersion;

    /** 组件列表 */
    private List<String> components;

    /** HDFS数据目录列表 */
    private List<String> hdfsDataDirs;

    /** HDFS副本数 */
    private Integer hdfsReplication;

    /** HDFS块大小 (MB) */
    private Integer hdfsBlockSizeMb;

    /** YARN NodeManager内存 (MB) */
    private Integer yarnMemory;

    /** YARN NodeManager CPU核数 */
    private Integer yarnCpu;

    // ==================== 端口配置 ====================

    /** NameNode RPC 端口 (默认 9000) */
    private Integer nameNodePort;

    /** NameNode HTTP 端口 (默认 9870) */
    private Integer nameNodeHttpPort;

    /** DataNode 端口 (默认 9866) */
    private Integer dataNodePort;

    /** Secondary NameNode HTTP 端口 (默认 9868) */
    private Integer secondaryNameNodeHttpPort;

    /** ResourceManager RPC 端口 (默认 8032) */
    private Integer resourceManagerPort;

    /** ResourceManager Web UI 端口 (默认 8088) */
    private Integer resourceManagerWebPort;

    /** NodeManager 端口 (默认 8042) */
    private Integer nodeManagerPort;

    /** JobHistory Server 端口 (默认 10020) */
    private Integer jobHistoryPort;

    /** JobHistory Web UI 端口 (默认 19888) */
    private Integer jobHistoryWebPort;

    // ==================== HA 配置 ====================

    /** HA模式备用Master主机 */
    private String haMasterHost;

    /** HA模式备用Master SSH端口 */
    private Integer haMasterPort;

    /** HA模式备用Master SSH用户名 */
    private String haMasterUser;

    /** HA模式备用Master SSH密码 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String haMasterPassword;

    /** ZooKeeper集群地址 */
    private String zkCluster;

    /** JVM参数 */
    private String jvmOpts;

    /** 关联的Master节点ID (仅Node部署时使用) */
    private Long masterId;

    /** 时区 */
    private String timezone;

    // ==================== 跨云部署配置 ====================

    /** 是否启用跨云部署模式（使用hostname而非IP） */
    private Boolean crossCloudMode;

    /** 集群节点hosts配置列表，格式: ["ip hostname", "ip2 hostname2"] */
    private List<String> clusterHosts;

    /** 当前节点的hostname（用于跨云部署） */
    private String nodeHostname;

    // ==================== 默认值方法 ====================

    public Integer getNameNodePort() {
        return nameNodePort != null ? nameNodePort : 9000;
    }

    public Integer getNameNodeHttpPort() {
        return nameNodeHttpPort != null ? nameNodeHttpPort : 9870;
    }

    public Integer getDataNodePort() {
        return dataNodePort != null ? dataNodePort : 9866;
    }

    public Integer getSecondaryNameNodeHttpPort() {
        return secondaryNameNodeHttpPort != null ? secondaryNameNodeHttpPort : 9868;
    }

    public Integer getResourceManagerPort() {
        return resourceManagerPort != null ? resourceManagerPort : 8032;
    }

    public Integer getResourceManagerWebPort() {
        return resourceManagerWebPort != null ? resourceManagerWebPort : 8088;
    }

    public Integer getNodeManagerPort() {
        return nodeManagerPort != null ? nodeManagerPort : 8042;
    }

    public Integer getJobHistoryPort() {
        return jobHistoryPort != null ? jobHistoryPort : 10020;
    }

    public Integer getJobHistoryWebPort() {
        return jobHistoryWebPort != null ? jobHistoryWebPort : 19888;
    }

    public String getJavaVersion() {
        return javaVersion != null ? javaVersion : "8";
    }
}
