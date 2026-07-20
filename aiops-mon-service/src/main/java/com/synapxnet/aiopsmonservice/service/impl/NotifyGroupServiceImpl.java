package com.synapxnet.aiopsmonservice.service.impl;

import com.synapxnet.aiopsmonservice.entity.NotifyGroup;
import com.synapxnet.aiopsmonservice.mapper.NotifyGroupMapper;
import com.synapxnet.aiopsmonservice.service.NotifyGroupService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotifyGroupServiceImpl implements NotifyGroupService {

    private final NotifyGroupMapper notifyGroupMapper;

    public NotifyGroupServiceImpl(NotifyGroupMapper notifyGroupMapper) {
        this.notifyGroupMapper = notifyGroupMapper;
    }

    @Override
    public List<NotifyGroup> listAll() {
        return notifyGroupMapper.findAll();
    }

    @Override
    public NotifyGroup getById(Long id) {
        NotifyGroup group = notifyGroupMapper.findById(id);
        if (group == null) {
            throw new IllegalArgumentException("Notify group not found: " + id);
        }
        return group;
    }

    @Override
    public NotifyGroup create(NotifyGroup notifyGroup) {
        if (notifyGroup.getNotifyType() == null) {
            notifyGroup.setNotifyType("email");
        }
        notifyGroupMapper.insert(notifyGroup);
        return notifyGroup;
    }

    @Override
    public NotifyGroup update(NotifyGroup notifyGroup) {
        notifyGroupMapper.update(notifyGroup);
        return notifyGroupMapper.findById(notifyGroup.getId());
    }

    @Override
    public void delete(Long id) {
        notifyGroupMapper.deleteById(id);
    }
}
