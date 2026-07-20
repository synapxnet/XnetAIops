package com.synapxnet.aiopsk8sservice.service.impl;

import com.synapxnet.aiopsk8sservice.entity.K8sAlertHistory;
import com.synapxnet.aiopsk8sservice.entity.K8sAlertRule;
import com.synapxnet.aiopsk8sservice.mapper.K8sAlertHistoryMapper;
import com.synapxnet.aiopsk8sservice.mapper.K8sAlertRuleMapper;
import com.synapxnet.aiopsk8sservice.service.K8sAlertService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class K8sAlertServiceImpl implements K8sAlertService {

    private final K8sAlertRuleMapper ruleMapper;
    private final K8sAlertHistoryMapper historyMapper;

    public K8sAlertServiceImpl(K8sAlertRuleMapper ruleMapper, K8sAlertHistoryMapper historyMapper) {
        this.ruleMapper = ruleMapper;
        this.historyMapper = historyMapper;
    }

    @Override
    public List<K8sAlertRule> listRules(Long clusterId) {
        return ruleMapper.findByClusterId(clusterId);
    }

    @Override
    public K8sAlertRule getRule(Long id) {
        return ruleMapper.findById(id);
    }

    @Override
    public void createRule(K8sAlertRule rule) {
        ruleMapper.insert(rule);
    }

    @Override
    public void updateRule(K8sAlertRule rule) {
        ruleMapper.update(rule);
    }

    @Override
    public void deleteRule(Long id) {
        ruleMapper.deleteById(id);
    }

    @Override
    public void toggleRule(Long id, boolean enabled) {
        K8sAlertRule rule = ruleMapper.findById(id);
        if (rule != null) {
            rule.setEnabled(enabled);
            ruleMapper.update(rule);
        }
    }

    @Override
    public List<K8sAlertHistory> listHistory(Long clusterId, int limit) {
        return historyMapper.findByClusterId(clusterId, limit > 0 ? limit : 100);
    }

    @Override
    public List<K8sAlertHistory> listHistoryByRule(Long ruleId, int limit) {
        return historyMapper.findByRuleId(ruleId, limit > 0 ? limit : 50);
    }
}
