package com.synapxnet.aiopsmonservice.service.impl;

import com.synapxnet.aiopsmonservice.entity.AlertRule;
import com.synapxnet.aiopsmonservice.mapper.AlertRuleMapper;
import com.synapxnet.aiopsmonservice.service.AlertRuleService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AlertRuleServiceImpl implements AlertRuleService {

    private final AlertRuleMapper alertRuleMapper;

    public AlertRuleServiceImpl(AlertRuleMapper alertRuleMapper) {
        this.alertRuleMapper = alertRuleMapper;
    }

    @Override
    public List<AlertRule> listByClusterId(Long clusterId) {
        return alertRuleMapper.findByClusterId(clusterId);
    }

    @Override
    public List<AlertRule> listAll() {
        return alertRuleMapper.findAll();
    }

    @Override
    public AlertRule getById(Long id) {
        AlertRule rule = alertRuleMapper.findById(id);
        if (rule == null) {
            throw new IllegalArgumentException("Alert rule not found: " + id);
        }
        return rule;
    }

    @Override
    public AlertRule create(AlertRule alertRule) {
        alertRule.setUid(UUID.randomUUID().toString());
        if (alertRule.getAlertLevel() == null) {
            alertRule.setAlertLevel("warning");
        }
        if (alertRule.getDurationSeconds() == null) {
            alertRule.setDurationSeconds(60);
        }
        if (alertRule.getEnabled() == null) {
            alertRule.setEnabled(true);
        }
        alertRuleMapper.insert(alertRule);
        return alertRule;
    }

    @Override
    public AlertRule update(AlertRule alertRule) {
        alertRuleMapper.update(alertRule);
        return alertRuleMapper.findById(alertRule.getId());
    }

    @Override
    public void delete(Long id) {
        alertRuleMapper.deleteById(id);
    }

    @Override
    public AlertRule toggleEnabled(Long id) {
        AlertRule rule = getById(id);
        Boolean newEnabled = !Boolean.TRUE.equals(rule.getEnabled());
        alertRuleMapper.updateEnabled(id, newEnabled);
        return alertRuleMapper.findById(id);
    }
}
