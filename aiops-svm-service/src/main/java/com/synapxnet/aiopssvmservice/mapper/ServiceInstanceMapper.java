package com.synapxnet.aiopssvmservice.mapper;

import com.synapxnet.aiopssvmservice.entity.RoleInstance;
import com.synapxnet.aiopssvmservice.entity.ServiceInstance;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ServiceInstanceMapper {

    // --- ServiceInstance ---

    @Select("SELECT * FROM xnet_aiops_svm_service_instance WHERE cluster_id = #{clusterId} ORDER BY created_at DESC")
    List<ServiceInstance> findByClusterId(@Param("clusterId") Long clusterId);

    @Select("SELECT * FROM xnet_aiops_svm_service_instance ORDER BY created_at DESC")
    List<ServiceInstance> findAll();

    @Select("SELECT * FROM xnet_aiops_svm_service_instance WHERE id = #{id}")
    ServiceInstance findById(@Param("id") Long id);

    @Insert("INSERT INTO xnet_aiops_svm_service_instance (uid, cluster_id, service_def_id, service_name, status, config_json) " +
            "VALUES (#{uid}, #{clusterId}, #{serviceDefId}, #{serviceName}, #{status}, #{configJson})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ServiceInstance serviceInstance);

    @Update("UPDATE xnet_aiops_svm_service_instance SET status=#{status}, config_json=#{configJson}, " +
            "config_version=#{configVersion}, need_restart=#{needRestart} WHERE id=#{id}")
    int update(ServiceInstance serviceInstance);

    @Delete("DELETE FROM xnet_aiops_svm_service_instance WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM xnet_aiops_svm_service_instance WHERE cluster_id = #{clusterId} AND status = 'running'")
    int countRunningByClusterId(@Param("clusterId") Long clusterId);

    // --- RoleInstance ---

    @Select("SELECT * FROM xnet_aiops_svm_role_instance WHERE service_instance_id = #{serviceInstanceId} ORDER BY created_at DESC")
    List<RoleInstance> findRolesByServiceInstanceId(@Param("serviceInstanceId") Long serviceInstanceId);

    @Select("SELECT * FROM xnet_aiops_svm_role_instance WHERE id = #{id}")
    RoleInstance findRoleById(@Param("id") Long id);

    @Insert("INSERT INTO xnet_aiops_svm_role_instance (uid, service_instance_id, role_def_id, role_name, role_type, host_id, hostname, status) " +
            "VALUES (#{uid}, #{serviceInstanceId}, #{roleDefId}, #{roleName}, #{roleType}, #{hostId}, #{hostname}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRole(RoleInstance roleInstance);

    @Update("UPDATE xnet_aiops_svm_role_instance SET status=#{status}, need_restart=#{needRestart} WHERE id=#{id}")
    int updateRole(RoleInstance roleInstance);

    @Delete("DELETE FROM xnet_aiops_svm_role_instance WHERE id = #{id}")
    int deleteRole(@Param("id") Long id);
}
