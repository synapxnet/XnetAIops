package com.synapxnet.aiopssvmservice.service;

import com.synapxnet.aiopssvmservice.entity.RoleInstance;
import com.synapxnet.aiopssvmservice.entity.ServiceInstance;

import java.util.List;
import java.util.Map;

public interface ServiceInstanceService {

    List<ServiceInstance> listByClusterId(Long clusterId);

    List<ServiceInstance> listAll();

    ServiceInstance getById(Long id);

    Map<String, Object> getDetail(Long id);

    ServiceInstance create(ServiceInstance serviceInstance);

    ServiceInstance update(ServiceInstance serviceInstance);

    void delete(Long id);

    List<RoleInstance> listRoles(Long serviceInstanceId);

    RoleInstance addRole(RoleInstance roleInstance);

    void removeRole(Long roleId);
}
