package com.synapxnet.aiopssvmservice.service;

import com.synapxnet.aiopssvmservice.entity.Framework;
import com.synapxnet.aiopssvmservice.entity.RoleDef;
import com.synapxnet.aiopssvmservice.entity.ServiceDef;

import java.util.List;

public interface FrameworkService {

    List<Framework> listAll();

    Framework getById(Long id);

    Framework create(Framework framework);

    Framework update(Framework framework);

    void delete(Long id);

    List<ServiceDef> listServiceDefs(Long frameworkId);

    ServiceDef createServiceDef(ServiceDef serviceDef);

    ServiceDef updateServiceDef(ServiceDef serviceDef);

    void deleteServiceDef(Long id);

    List<RoleDef> listRoleDefs(Long serviceDefId);

    RoleDef createRoleDef(RoleDef roleDef);

    RoleDef updateRoleDef(RoleDef roleDef);

    void deleteRoleDef(Long id);
}
