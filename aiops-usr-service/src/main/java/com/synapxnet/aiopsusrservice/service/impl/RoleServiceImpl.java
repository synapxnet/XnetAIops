package com.synapxnet.aiopsusrservice.service.impl;

import com.synapxnet.aiopsusrservice.entity.Role;
import com.synapxnet.aiopsusrservice.entity.UserRoleCluster;
import com.synapxnet.aiopsusrservice.mapper.RoleMapper;
import com.synapxnet.aiopsusrservice.service.RoleService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;

    public RoleServiceImpl(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    @Override
    public List<Role> listAll() {
        return roleMapper.findAll();
    }

    @Override
    public Role getById(Long id) {
        Role role = roleMapper.findById(id);
        if (role == null) {
            throw new IllegalArgumentException("Role not found: " + id);
        }
        return role;
    }

    @Override
    public Role create(Role role) {
        Role existing = roleMapper.findByCode(role.getRoleCode());
        if (existing != null) {
            throw new IllegalArgumentException("Role code already exists: " + role.getRoleCode());
        }
        roleMapper.insert(role);
        return role;
    }

    @Override
    public Role update(Role role) {
        roleMapper.update(role);
        return roleMapper.findById(role.getId());
    }

    @Override
    public void delete(Long id) {
        roleMapper.deleteMappingsByRoleId(id);
        roleMapper.deleteById(id);
    }

    @Override
    public List<UserRoleCluster> getUserRoles(Long userId) {
        return roleMapper.findByUserId(userId);
    }

    @Override
    public List<UserRoleCluster> getRoleUsers(Long roleId) {
        return roleMapper.findByRoleId(roleId);
    }

    @Override
    public UserRoleCluster assignRole(UserRoleCluster mapping) {
        roleMapper.insertMapping(mapping);
        return mapping;
    }

    @Override
    public void removeMapping(Long mappingId) {
        roleMapper.deleteMapping(mappingId);
    }
}
