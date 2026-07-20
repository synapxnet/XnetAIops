package com.synapxnet.aiopsusrservice.mapper;

import com.synapxnet.aiopsusrservice.entity.Role;
import com.synapxnet.aiopsusrservice.entity.UserRoleCluster;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RoleMapper {

    // --- Role ---

    @Select("SELECT * FROM xnet_aiops_usr_role ORDER BY created_at DESC")
    List<Role> findAll();

    @Select("SELECT * FROM xnet_aiops_usr_role WHERE id = #{id}")
    Role findById(@Param("id") Long id);

    @Select("SELECT * FROM xnet_aiops_usr_role WHERE role_code = #{roleCode}")
    Role findByCode(@Param("roleCode") String roleCode);

    @Insert("INSERT INTO xnet_aiops_usr_role (role_name, role_code, description) " +
            "VALUES (#{roleName}, #{roleCode}, #{description})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Role role);

    @Update("UPDATE xnet_aiops_usr_role SET role_name=#{roleName}, description=#{description} WHERE id=#{id}")
    int update(Role role);

    @Delete("DELETE FROM xnet_aiops_usr_role WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    // --- UserRoleCluster ---

    @Select("SELECT urc.*, u.username, r.role_name, r.role_code " +
            "FROM xnet_aiops_usr_user_role_cluster urc " +
            "JOIN xnet_aiops_usr_user u ON urc.user_id = u.id " +
            "JOIN xnet_aiops_usr_role r ON urc.role_id = r.id " +
            "WHERE urc.user_id = #{userId}")
    List<UserRoleCluster> findByUserId(@Param("userId") Long userId);

    @Select("SELECT urc.*, u.username, r.role_name, r.role_code " +
            "FROM xnet_aiops_usr_user_role_cluster urc " +
            "JOIN xnet_aiops_usr_user u ON urc.user_id = u.id " +
            "JOIN xnet_aiops_usr_role r ON urc.role_id = r.id " +
            "WHERE urc.role_id = #{roleId}")
    List<UserRoleCluster> findByRoleId(@Param("roleId") Long roleId);

    @Insert("INSERT INTO xnet_aiops_usr_user_role_cluster (user_id, role_id, cluster_id) " +
            "VALUES (#{userId}, #{roleId}, #{clusterId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertMapping(UserRoleCluster mapping);

    @Delete("DELETE FROM xnet_aiops_usr_user_role_cluster WHERE id = #{id}")
    int deleteMapping(@Param("id") Long id);

    @Delete("DELETE FROM xnet_aiops_usr_user_role_cluster WHERE role_id = #{roleId}")
    int deleteMappingsByRoleId(@Param("roleId") Long roleId);
}
