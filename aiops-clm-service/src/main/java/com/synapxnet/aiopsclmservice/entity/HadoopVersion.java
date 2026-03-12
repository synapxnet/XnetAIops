package com.synapxnet.aiopsclmservice.entity;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Hadoop 版本实体类
 * 存储从 Apache Hadoop 官网获取的版本信息
 */
@Data
public class HadoopVersion {

    /** 主键ID */
    private Long id;

    /** 版本号 (如: 3.3.6) */
    private String version;

    /** 版本类型 (stable/alpha/beta) */
    private String versionType;

    /** 发布日期 */
    private LocalDate releaseDate;

    /** 下载URL */
    private String downloadUrl;

    /** 是否为最新版本 */
    private Boolean isLatest;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
