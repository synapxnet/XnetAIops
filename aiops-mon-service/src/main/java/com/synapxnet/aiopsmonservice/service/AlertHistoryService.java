package com.synapxnet.aiopsmonservice.service;

import com.synapxnet.aiopsmonservice.entity.AlertHistory;

import java.util.List;
import java.util.Map;

public interface AlertHistoryService {

    List<AlertHistory> list(Long clusterId, String status);

    AlertHistory getById(Long id);

    /**
     * 根据稳定 UID 获取告警记录。
     *
     * @param uid 告警稳定 UID
     * @return 告警领域记录
     */
    AlertHistory getByUid(String uid);

    AlertHistory create(AlertHistory alertHistory);

    AlertHistory acknowledge(Long id);

    AlertHistory resolve(Long id);

    Map<String, Object> getSummary();
}
