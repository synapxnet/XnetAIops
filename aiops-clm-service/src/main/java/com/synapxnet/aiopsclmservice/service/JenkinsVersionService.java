package com.synapxnet.aiopsclmservice.service;

import com.synapxnet.aiopsclmservice.entity.JenkinsVersion;

import java.util.List;
import java.util.Map;

/**
 * Jenkins 版本服务接口
 */
public interface JenkinsVersionService {
    List<JenkinsVersion> getStableVersions();

    List<JenkinsVersion> getLtsVersions();

    Map<String, Object> refreshVersions();

    Map<String, Object> getVersionStats();
}
