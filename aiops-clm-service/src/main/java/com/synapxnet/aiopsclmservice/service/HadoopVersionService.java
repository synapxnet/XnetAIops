package com.synapxnet.aiopsclmservice.service;

import com.synapxnet.aiopsclmservice.entity.HadoopVersion;
import java.util.List;
import java.util.Map;

/**
 * Hadoop 版本服务接口
 */
public interface HadoopVersionService {
    List<HadoopVersion> getAllVersions();

    List<HadoopVersion> getStableVersions();

    Map<String, Object> refreshVersions();

    Map<String, Object> getVersionStats();
}
