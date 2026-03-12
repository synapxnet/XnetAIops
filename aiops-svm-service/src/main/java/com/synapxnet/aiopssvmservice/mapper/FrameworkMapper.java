package com.synapxnet.aiopssvmservice.mapper;

import com.synapxnet.aiopssvmservice.entity.Framework;
import com.synapxnet.aiopssvmservice.entity.RoleDef;
import com.synapxnet.aiopssvmservice.entity.ServiceDef;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface FrameworkMapper {

    // --- Framework ---

    @Select("SELECT * FROM xnet_aiops_svm_framework ORDER BY created_at DESC")
    List<Framework> findAll();

    @Select("SELECT * FROM xnet_aiops_svm_framework WHERE id = #{id}")
    Framework findById(@Param("id") Long id);

    @Select("SELECT * FROM xnet_aiops_svm_framework WHERE frame_code = #{frameCode}")
    Framework findByCode(@Param("frameCode") String frameCode);

    @Insert("INSERT INTO xnet_aiops_svm_framework (frame_name, frame_code, frame_version, description) " +
            "VALUES (#{frameName}, #{frameCode}, #{frameVersion}, #{description})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Framework framework);

    @Update("UPDATE xnet_aiops_svm_framework SET frame_name=#{frameName}, frame_version=#{frameVersion}, " +
            "description=#{description} WHERE id=#{id}")
    int update(Framework framework);

    @Delete("DELETE FROM xnet_aiops_svm_framework WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    // --- ServiceDef ---

    @Select("SELECT * FROM xnet_aiops_svm_service_def WHERE framework_id = #{frameworkId} ORDER BY sort_order")
    List<ServiceDef> findServiceDefsByFrameworkId(@Param("frameworkId") Long frameworkId);

    @Select("SELECT * FROM xnet_aiops_svm_service_def WHERE id = #{id}")
    ServiceDef findServiceDefById(@Param("id") Long id);

    @Insert("INSERT INTO xnet_aiops_svm_service_def (framework_id, service_name, service_label, service_version, " +
            "description, dependencies, package_name, config_json, sort_order) " +
            "VALUES (#{frameworkId}, #{serviceName}, #{serviceLabel}, #{serviceVersion}, " +
            "#{description}, #{dependencies}, #{packageName}, #{configJson}, #{sortOrder})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertServiceDef(ServiceDef serviceDef);

    @Update("UPDATE xnet_aiops_svm_service_def SET service_name=#{serviceName}, service_label=#{serviceLabel}, " +
            "service_version=#{serviceVersion}, description=#{description}, dependencies=#{dependencies}, " +
            "package_name=#{packageName}, config_json=#{configJson}, sort_order=#{sortOrder} WHERE id=#{id}")
    int updateServiceDef(ServiceDef serviceDef);

    @Delete("DELETE FROM xnet_aiops_svm_service_def WHERE id = #{id}")
    int deleteServiceDef(@Param("id") Long id);

    // --- RoleDef ---

    @Select("SELECT * FROM xnet_aiops_svm_role_def WHERE service_def_id = #{serviceDefId}")
    List<RoleDef> findRoleDefsByServiceDefId(@Param("serviceDefId") Long serviceDefId);

    @Insert("INSERT INTO xnet_aiops_svm_role_def (service_def_id, role_name, role_type, cardinality, jmx_port, log_file) " +
            "VALUES (#{serviceDefId}, #{roleName}, #{roleType}, #{cardinality}, #{jmxPort}, #{logFile})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRoleDef(RoleDef roleDef);

    @Update("UPDATE xnet_aiops_svm_role_def SET role_name=#{roleName}, role_type=#{roleType}, " +
            "cardinality=#{cardinality}, jmx_port=#{jmxPort}, log_file=#{logFile} WHERE id=#{id}")
    int updateRoleDef(RoleDef roleDef);

    @Delete("DELETE FROM xnet_aiops_svm_role_def WHERE id = #{id}")
    int deleteRoleDef(@Param("id") Long id);
}
