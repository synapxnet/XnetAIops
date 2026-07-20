package com.synapxnet.aiopsclmservice.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Hadoop 集群节点实体类
 * 存储 Master 和 Node 节点的配置信息
 */
@Data
public class HadoopCluster {

    /** 主键ID */
    private Long id;

    /** 唯一标识符 */
    private String uid;

    /** 节点名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 主机地址 */
    private String host;

    /** SSH端口 */
    private Integer port;

    /** SSH用户名 */
    private String sshUser;

    /** SSH密码 (写入时不返回) */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String sshPassword;

    /** SSH私钥 (写入时不返回) */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String sshPrivateKey;

    /** Hadoop版本 */
    private String hadoopVersion;

    /** 操作系统类型 (centos7/centos8/ubuntu20/ubuntu22/debian11/debian12) */
    private String osType;

    /** 节点类型 (master/node) */
    private String nodeType;

    /** 部署模式 (standard/ha) */
    private String deployMode;

    /** 组件列表 JSON (["hdfs", "yarn", "mapreduce"]) */
    private String components;

    /** HDFS数据目录 JSON (["/data1", "/data2"]) */
    private String hdfsDataDirs;

    /** HDFS副本数 */
    private Integer hdfsReplication;

    /** HDFS块大小 (字节) */
    private Long hdfsBlockSize;

    /** YARN NodeManager内存 (MB) */
    private Integer yarnMemory;

    /** YARN NodeManager CPU核数 */
    private Integer yarnCpu;

    /** HA模式备用Master主机 */
    private String haMasterHost;

    /** ZooKeeper集群地址 */
    private String zkCluster;

    /** 状态 (created/deploying/deployed/running/stopped/failed) */
    private String status;

    /** 部署日志 */
    private String deployLog;

    /** 关联的Master节点ID (仅Node类型使用) */
    private Long masterId;

    /** 创建者 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
