package com.synapxnet.aiopssvmservice.service.impl;

import com.synapxnet.aiopssvmservice.entity.Framework;
import com.synapxnet.aiopssvmservice.entity.RoleDef;
import com.synapxnet.aiopssvmservice.entity.ServiceDef;
import com.synapxnet.aiopssvmservice.mapper.FrameworkMapper;
import com.synapxnet.aiopssvmservice.service.FrameworkService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FrameworkServiceImpl implements FrameworkService {

    private final FrameworkMapper frameworkMapper;

    public FrameworkServiceImpl(FrameworkMapper frameworkMapper) {
        this.frameworkMapper = frameworkMapper;
    }

    @Override
    public List<Framework> listAll() {
        return frameworkMapper.findAll();
    }

    @Override
    public Framework getById(Long id) {
        Framework framework = frameworkMapper.findById(id);
        if (framework == null) {
            throw new IllegalArgumentException("Framework not found: " + id);
        }
        return framework;
    }

    @Override
    public Framework create(Framework framework) {
        Framework existing = frameworkMapper.findByCode(framework.getFrameCode());
        if (existing != null) {
            throw new IllegalArgumentException("Framework code already exists: " + framework.getFrameCode());
        }
        frameworkMapper.insert(framework);
        return framework;
    }

    @Override
    public Framework update(Framework framework) {
        frameworkMapper.update(framework);
        return frameworkMapper.findById(framework.getId());
    }

    @Override
    public void delete(Long id) {
        frameworkMapper.deleteById(id);
    }

    @Override
    public List<ServiceDef> listServiceDefs(Long frameworkId) {
        return frameworkMapper.findServiceDefsByFrameworkId(frameworkId);
    }

    @Override
    public ServiceDef createServiceDef(ServiceDef serviceDef) {
        if (serviceDef.getSortOrder() == null) {
            serviceDef.setSortOrder(0);
        }
        frameworkMapper.insertServiceDef(serviceDef);
        return serviceDef;
    }

    @Override
    public ServiceDef updateServiceDef(ServiceDef serviceDef) {
        frameworkMapper.updateServiceDef(serviceDef);
        return frameworkMapper.findServiceDefById(serviceDef.getId());
    }

    @Override
    public void deleteServiceDef(Long id) {
        frameworkMapper.deleteServiceDef(id);
    }

    @Override
    public List<RoleDef> listRoleDefs(Long serviceDefId) {
        return frameworkMapper.findRoleDefsByServiceDefId(serviceDefId);
    }

    @Override
    public RoleDef createRoleDef(RoleDef roleDef) {
        frameworkMapper.insertRoleDef(roleDef);
        return roleDef;
    }

    @Override
    public RoleDef updateRoleDef(RoleDef roleDef) {
        frameworkMapper.updateRoleDef(roleDef);
        return roleDef;
    }

    @Override
    public void deleteRoleDef(Long id) {
        frameworkMapper.deleteRoleDef(id);
    }
}
