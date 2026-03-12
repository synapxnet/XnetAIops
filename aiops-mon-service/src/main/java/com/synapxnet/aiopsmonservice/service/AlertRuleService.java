package com.synapxnet.aiopsmonservice.service;

import com.synapxnet.aiopsmonservice.entity.AlertRule;

import java.util.List;

public interface AlertRuleService {

    List<AlertRule> listByClusterId(Long clusterId);

    List<AlertRule> listAll();

    AlertRule getById(Long id);

    AlertRule create(AlertRule alertRule);

    AlertRule update(AlertRule alertRule);

    void delete(Long id);

    AlertRule toggleEnabled(Long id);
}
