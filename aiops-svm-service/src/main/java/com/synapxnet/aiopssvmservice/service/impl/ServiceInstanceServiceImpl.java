package com.synapxnet.aiopssvmservice.service.impl;

import com.synapxnet.aiopssvmservice.entity.Command;
import com.synapxnet.aiopssvmservice.entity.RoleInstance;
import com.synapxnet.aiopssvmservice.entity.ServiceInstance;
import com.synapxnet.aiopssvmservice.mapper.CommandMapper;
import com.synapxnet.aiopssvmservice.mapper.FrameworkMapper;
import com.synapxnet.aiopssvmservice.mapper.ServiceInstanceMapper;
import com.synapxnet.aiopssvmservice.service.ServiceInstanceService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ServiceInstanceServiceImpl implements ServiceInstanceService {

    private final ServiceInstanceMapper serviceInstanceMapper;
    private final CommandMapper commandMapper;
    private final FrameworkMapper frameworkMapper;

    public ServiceInstanceServiceImpl(ServiceInstanceMapper serviceInstanceMapper,
                                       CommandMapper commandMapper,
                                       FrameworkMapper frameworkMapper) {
        this.serviceInstanceMapper = serviceInstanceMapper;
        this.commandMapper = commandMapper;
        this.frameworkMapper = frameworkMapper;
    }

    @Override
    public List<ServiceInstance> listByClusterId(Long clusterId) {
        return serviceInstanceMapper.findByClusterId(clusterId);
    }

    @Override
    public List<ServiceInstance> listAll() {
        return serviceInstanceMapper.findAll();
    }

    @Override
    public ServiceInstance getById(Long id) {
        ServiceInstance instance = serviceInstanceMapper.findById(id);
        if (instance == null) {
            throw new IllegalArgumentException("Service instance not found: " + id);
        }
        return instance;
    }

    /**
     * 根据稳定 UID 获取服务实例，供 Agent 健康证据工具使用。
     *
     * @param uid 服务实例 UID
     * @return 服务领域记录
     */
    @Override
    public ServiceInstance getByUid(String uid) {
        ServiceInstance instance = serviceInstanceMapper.findByUid(uid);
        if (instance == null) {
            throw new IllegalArgumentException("Service instance not found: " + uid);
        }
        return instance;
    }

    @Override
    public Map<String, Object> getDetail(Long id) {
        ServiceInstance instance = getById(id);
        List<RoleInstance> roles = serviceInstanceMapper.findRolesByServiceInstanceId(id);
        List<Command> commands = commandMapper.findByServiceInstanceId(id);

        Map<String, Object> detail = new HashMap<>();
        detail.put("service", instance);
        detail.put("roles", roles);
        detail.put("commands", commands);

        // Include ServiceDef info if available
        if (instance.getServiceDefId() != null) {
            detail.put("serviceDef", frameworkMapper.findServiceDefById(instance.getServiceDefId()));
        }

        return detail;
    }

    @Override
    public ServiceInstance create(ServiceInstance serviceInstance) {
        serviceInstance.setUid(UUID.randomUUID().toString());
        if (serviceInstance.getStatus() == null) {
            serviceInstance.setStatus("not_installed");
        }
        serviceInstanceMapper.insert(serviceInstance);
        return serviceInstance;
    }

    @Override
    public ServiceInstance update(ServiceInstance serviceInstance) {
        serviceInstanceMapper.update(serviceInstance);
        return serviceInstanceMapper.findById(serviceInstance.getId());
    }

    @Override
    public void delete(Long id) {
        serviceInstanceMapper.deleteById(id);
    }

    @Override
    public List<RoleInstance> listRoles(Long serviceInstanceId) {
        return serviceInstanceMapper.findRolesByServiceInstanceId(serviceInstanceId);
    }

    @Override
    public RoleInstance addRole(RoleInstance roleInstance) {
        roleInstance.setUid(UUID.randomUUID().toString());
        if (roleInstance.getStatus() == null) {
            roleInstance.setStatus("stopped");
        }
        serviceInstanceMapper.insertRole(roleInstance);
        return roleInstance;
    }

    @Override
    public void removeRole(Long roleId) {
        serviceInstanceMapper.deleteRole(roleId);
    }
}
