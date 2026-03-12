package com.synapxnet.aiopsclmservice.mapper;

import com.synapxnet.aiopsclmservice.entity.JenkinsMaster;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

/**
 * Jenkins Master Mapper
 */
@Mapper
public interface JenkinsMasterMapper {

    @Select("SELECT * FROM xnet_aiops_clm_jenkins_master ORDER BY created_at DESC")
    List<JenkinsMaster> findAll();

    @Select("SELECT * FROM xnet_aiops_clm_jenkins_master WHERE id = #{id}")
    Optional<JenkinsMaster> findById(Long id);

    @Select("SELECT * FROM xnet_aiops_clm_jenkins_master WHERE uid = #{uid}")
    Optional<JenkinsMaster> findByUid(String uid);

    @Select("SELECT * FROM xnet_aiops_clm_jenkins_master WHERE name = #{name}")
    Optional<JenkinsMaster> findByName(String name);

    @Select("SELECT * FROM xnet_aiops_clm_jenkins_master WHERE host = #{host} AND jenkins_port = #{jenkinsPort}")
    Optional<JenkinsMaster> findByHostAndJenkinsPort(@Param("host") String host,
            @Param("jenkinsPort") Integer jenkinsPort);

    @Select("SELECT * FROM xnet_aiops_clm_jenkins_master WHERE status = #{status} ORDER BY created_at DESC")
    List<JenkinsMaster> findByStatus(String status);

    @Select("SELECT * FROM xnet_aiops_clm_jenkins_master WHERE status IN ('deployed', 'running') ORDER BY created_at DESC")
    List<JenkinsMaster> findDeployedMasters();

    @Insert("INSERT INTO xnet_aiops_clm_jenkins_master (" +
            "uid, name, host, port, username, encrypted_password, os_type, " +
            "jenkins_port, jenkins_home, jenkins_version, java_version, java_opts, " +
            "admin_username, encrypted_admin_password, credentials_config, " +
            "status, initial_password, deploy_log, " +
            "region, cpu_cores, ram_gb, disk_gb, " +
            "host_id, tenant_uid, description, created_by, updated_by" +
            ") VALUES (" +
            "#{uid}, #{name}, #{host}, #{port}, #{username}, #{encrypted_password}, #{os_type}, " +
            "#{jenkins_port}, #{jenkins_home}, #{jenkins_version}, #{java_version}, #{java_opts}, " +
            "#{admin_username}, #{encrypted_admin_password}, #{credentials_config}, " +
            "#{status}, #{initial_password}, #{deploy_log}, " +
            "#{region}, #{cpu_cores}, #{ram_gb}, #{disk_gb}, " +
            "#{host_id}, #{tenant_uid}, #{description}, #{created_by}, #{updated_by}" +
            ")")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(JenkinsMaster jenkinsMaster);

    @Update("UPDATE xnet_aiops_clm_jenkins_master SET " +
            "name = #{name}, host = #{host}, port = #{port}, " +
            "username = #{username}, encrypted_password = #{encrypted_password}, " +
            "os_type = #{os_type}, jenkins_port = #{jenkins_port}, " +
            "jenkins_home = #{jenkins_home}, jenkins_version = #{jenkins_version}, " +
            "java_version = #{java_version}, java_opts = #{java_opts}, " +
            "admin_username = #{admin_username}, encrypted_admin_password = #{encrypted_admin_password}, " +
            "credentials_config = #{credentials_config}, status = #{status}, " +
            "initial_password = #{initial_password}, deploy_log = #{deploy_log}, " +
            "region = #{region}, cpu_cores = #{cpu_cores}, ram_gb = #{ram_gb}, disk_gb = #{disk_gb}, " +
            "host_id = #{host_id}, description = #{description}, updated_by = #{updated_by}, " +
            "updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id}")
    int update(JenkinsMaster jenkinsMaster);

    @Update("UPDATE xnet_aiops_clm_jenkins_master SET " +
            "status = #{status}, deploy_log = #{deployLog}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("deployLog") String deployLog);

    @Update("UPDATE xnet_aiops_clm_jenkins_master SET " +
            "initial_password = #{initialPassword}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id}")
    int updateInitialPassword(@Param("id") Long id, @Param("initialPassword") String initialPassword);

    @Update("UPDATE xnet_aiops_clm_jenkins_master SET " +
            "last_heartbeat = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id}")
    int updateHeartbeat(Long id);

    @Update("UPDATE xnet_aiops_clm_jenkins_master SET " +
            "credentials_config = #{credentialsConfig}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id}")
    int updateCredentialsConfig(@Param("id") Long id, @Param("credentialsConfig") String credentialsConfig);

    @Update("UPDATE xnet_aiops_clm_jenkins_master SET " +
            "encrypted_password = #{encryptedPassword}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id}")
    int updateEncryptedPassword(@Param("id") Long id, @Param("encryptedPassword") String encryptedPassword);

    @Delete("DELETE FROM xnet_aiops_clm_jenkins_master WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT COUNT(*) FROM xnet_aiops_clm_jenkins_master WHERE name = #{name} AND (#{excludeId} IS NULL OR id != #{excludeId})")
    int countByName(@Param("name") String name, @Param("excludeId") Long excludeId);

    @Select("SELECT COUNT(*) FROM xnet_aiops_clm_jenkins_master WHERE host = #{host} AND jenkins_port = #{jenkinsPort} AND (#{excludeId} IS NULL OR id != #{excludeId})")
    int countByHostAndJenkinsPort(@Param("host") String host, @Param("jenkinsPort") Integer jenkinsPort,
            @Param("excludeId") Long excludeId);
}
