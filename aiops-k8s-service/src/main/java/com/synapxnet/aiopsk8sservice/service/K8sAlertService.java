package com.synapxnet.aiopsk8sservice.service;

import com.synapxnet.aiopsk8sservice.entity.K8sAlertHistory;
import com.synapxnet.aiopsk8sservice.entity.K8sAlertRule;

import java.util.List;

public interface K8sAlertService {
    List<K8sAlertRule> listRules(Long clusterId);
    K8sAlertRule getRule(Long id);
    void createRule(K8sAlertRule rule);
    void updateRule(K8sAlertRule rule);
    void deleteRule(Long id);
    void toggleRule(Long id, boolean enabled);

    List<K8sAlertHistory> listHistory(Long clusterId, int limit);
    List<K8sAlertHistory> listHistoryByRule(Long ruleId, int limit);
}
