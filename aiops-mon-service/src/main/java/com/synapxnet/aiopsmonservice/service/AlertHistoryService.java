package com.synapxnet.aiopsmonservice.service;

import com.synapxnet.aiopsmonservice.entity.AlertHistory;

import java.util.List;
import java.util.Map;

public interface AlertHistoryService {

    List<AlertHistory> list(Long clusterId, String status);

    AlertHistory getById(Long id);

    AlertHistory create(AlertHistory alertHistory);

    AlertHistory acknowledge(Long id);

    AlertHistory resolve(Long id);

    Map<String, Object> getSummary();
}
