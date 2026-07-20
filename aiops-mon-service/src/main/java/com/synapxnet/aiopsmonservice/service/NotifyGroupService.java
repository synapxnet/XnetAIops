package com.synapxnet.aiopsmonservice.service;

import com.synapxnet.aiopsmonservice.entity.NotifyGroup;

import java.util.List;

public interface NotifyGroupService {

    List<NotifyGroup> listAll();

    NotifyGroup getById(Long id);

    NotifyGroup create(NotifyGroup notifyGroup);

    NotifyGroup update(NotifyGroup notifyGroup);

    void delete(Long id);
}
