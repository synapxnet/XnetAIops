package com.synapxnet.aiopsclmservice.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class JenkinsNodeDeployConfig {
    private String jenkinsUrl; // Jenkins服务器URL
    private String agentName; // Agent名称
    private String workDir; // 工作目录
    private String javaVersion; // Java版本 (8, 11, 17, 21)
    private String pythonVersion; // Python版本 (3.8, 3.9, 3.10, 3.11, 3.12)
    private String agentVersion; // Jenkins Agent版本
    private String labels; // 节点标签

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String jenkinsSecret; // Jenkins Agent Secret（可选，用于JNLP连接）

    // 额外配置
    private Boolean installDocker; // 是否安装Docker
    private Boolean installGit; // 是否安装Git
    private Boolean installMaven; // 是否安装Maven
    private String mavenVersion; // Maven版本
    private Boolean installNode; // 是否安装Node.js
    private String nodeVersion; // Node.js版本

    // 镜像源配置
    private Boolean useDomesticMirror; // 是否使用国内镜像源（默认true）
}
