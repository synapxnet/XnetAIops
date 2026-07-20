package com.synapxnet.aiopsclmservice.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Date;

@Data
public class JenkinsNode {
    private Long id;
    private String uid;
    private String name; // 节点名称
    private String host; // IP地址
    private Integer port; // SSH端口
    private String username; // SSH用户名

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String encrypted_password; // 加密的SSH密码(Base64编码)
    private String os_type; // 操作系统类型: linux, macos, windows
    private String region; // 地域
    private String container_type; // 容器类型: cce, docker
    private String resource_type; // 资源类型: cpu, single_gpu, multi_gpu
    private String resource_spec; // 资源规格ID
    private Integer cpu_cores; // CPU核数
    private Integer ram_gb; // 内存大小(GB)
    private Integer gpu_memory; // GPU显存(GB)
    private String gpu_model; // GPU型号
    private Integer gpu_count; // GPU数量
    private String status; // 状态: pending, deploying, deployed, failed, offline
    private String jenkins_url; // Jenkins服务器URL
    private String agent_name; // Jenkins Agent名称
    private String work_dir; // 工作目录
    private String java_version; // Java版本
    private String python_version; // Python版本
    private String agent_version; // Jenkins Agent版本
    private String labels; // 节点标签（逗号分隔）
    private String description; // 描述
    private String deploy_log; // 部署日志
    private Date last_heartbeat; // 最后心跳时间

    private String created_by;
    private String updated_by;
    private Date created_at;
    private Date updated_at;

    // 非数据库字段 - 密码只写不读
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password; // 明文密码（仅用于传输）
}
