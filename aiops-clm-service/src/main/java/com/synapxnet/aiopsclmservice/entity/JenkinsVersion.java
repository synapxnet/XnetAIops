package com.synapxnet.aiopsclmservice.entity;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Jenkins 版本实体类
 * 存储从 mirrors.jenkins.io 获取的版本信息
 */
@Data
public class JenkinsVersion {

    /** 主键ID */
    private Long id;

    /** 版本号 (如: 2.462.3) */
    private String version;

    /** 版本类型 (stable/weekly) */
    private String versionType;

    /** 发布日期 */
    private LocalDate releaseDate;

    /** 下载URL */
    private String downloadUrl;

    /** SHA256校验值 */
    private String sha256;

    /** 是否为LTS版本 */
    private Boolean isLts;

    /** 是否为最新版本 */
    private Boolean isLatest;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
