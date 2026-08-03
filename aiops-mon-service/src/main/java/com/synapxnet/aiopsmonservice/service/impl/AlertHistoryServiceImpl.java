package com.synapxnet.aiopsmonservice.service.impl;

import com.synapxnet.aiopsmonservice.entity.AlertHistory;
import com.synapxnet.aiopsmonservice.mapper.AlertHistoryMapper;
import com.synapxnet.aiopsmonservice.service.AlertHistoryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AlertHistoryServiceImpl implements AlertHistoryService {

    private final AlertHistoryMapper alertHistoryMapper;

    public AlertHistoryServiceImpl(AlertHistoryMapper alertHistoryMapper) {
        this.alertHistoryMapper = alertHistoryMapper;
    }

    @Override
    public List<AlertHistory> list(Long clusterId, String status) {
        if (clusterId != null && status != null) {
            return alertHistoryMapper.findByClusterIdAndStatus(clusterId, status);
        }
        if (clusterId != null) {
            return alertHistoryMapper.findByClusterId(clusterId);
        }
        return alertHistoryMapper.findAll();
    }

    @Override
    public AlertHistory getById(Long id) {
        AlertHistory alert = alertHistoryMapper.findById(id);
        if (alert == null) {
            throw new IllegalArgumentException("Alert not found: " + id);
        }
        return alert;
    }

    /**
     * 根据稳定 UID 获取告警记录，避免在 Controller 中遍历告警列表。
     *
     * @param uid 告警稳定 UID
     * @return 告警领域记录
     */
    @Override
    public AlertHistory getByUid(String uid) {
        AlertHistory alert = alertHistoryMapper.findByUid(uid);
        if (alert == null) {
            throw new IllegalArgumentException("Alert not found: " + uid);
        }
        return alert;
    }

    @Override
    public AlertHistory create(AlertHistory alertHistory) {
        alertHistory.setUid(UUID.randomUUID().toString());
        if (alertHistory.getStatus() == null) {
            alertHistory.setStatus("open");
        }
        alertHistoryMapper.insert(alertHistory);
        return alertHistory;
    }

    @Override
    public AlertHistory acknowledge(Long id) {
        AlertHistory alert = getById(id);
        alert.setStatus("acknowledged");
        alertHistoryMapper.updateStatus(alert);
        return alertHistoryMapper.findById(id);
    }

    @Override
    public AlertHistory resolve(Long id) {
        AlertHistory alert = getById(id);
        alert.setStatus("resolved");
        alert.setResolvedAt(LocalDateTime.now());
        alertHistoryMapper.updateStatus(alert);
        return alertHistoryMapper.findById(id);
    }

    @Override
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalAlerts", alertHistoryMapper.countAll());
        summary.put("openAlerts", alertHistoryMapper.countOpen());
        summary.put("criticalAlerts", alertHistoryMapper.countOpenCritical());
        return summary;
    }
}
