package com.synapxnet.aiopsusrservice.service;

import com.synapxnet.aiopsusrservice.entity.Role;
import com.synapxnet.aiopsusrservice.entity.UserRoleCluster;

import java.util.List;

public interface RoleService {

    List<Role> listAll();

    Role getById(Long id);

    Role create(Role role);

    Role update(Role role);

    void delete(Long id);

    List<UserRoleCluster> getUserRoles(Long userId);

    List<UserRoleCluster> getRoleUsers(Long roleId);

    UserRoleCluster assignRole(UserRoleCluster mapping);

    void removeMapping(Long mappingId);
}
