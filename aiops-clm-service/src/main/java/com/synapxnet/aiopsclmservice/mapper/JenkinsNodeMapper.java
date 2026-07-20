package com.synapxnet.aiopsclmservice.mapper;

import com.synapxnet.aiopsclmservice.entity.JenkinsNode;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Optional;

@Mapper
public interface JenkinsNodeMapper {

    @Select("SELECT * FROM xnet_aiops_clm_jenkins_node ORDER BY created_at DESC")
    List<JenkinsNode> findAll();

    @Select("SELECT * FROM xnet_aiops_clm_jenkins_node WHERE id = #{id}")
    Optional<JenkinsNode> findById(Long id);

    @Select("SELECT * FROM xnet_aiops_clm_jenkins_node WHERE uid = #{uid}")
    Optional<JenkinsNode> findByUid(String uid);

    @Select("SELECT * FROM xnet_aiops_clm_jenkins_node WHERE name = #{name}")
    Optional<JenkinsNode> findByName(String name);

    @Select("SELECT * FROM xnet_aiops_clm_jenkins_node WHERE host = #{host} AND port = #{port}")
    Optional<JenkinsNode> findByHostAndPort(@Param("host") String host, @Param("port") Integer port);

    @Select("SELECT * FROM xnet_aiops_clm_jenkins_node WHERE status = #{status}")
    List<JenkinsNode> findByStatus(String status);

    @Insert("INSERT INTO xnet_aiops_clm_jenkins_node (" +
            "uid, name, host, port, username, encrypted_password, os_type, region, " +
            "container_type, resource_type, resource_spec, cpu_cores, ram_gb, gpu_memory, gpu_model, gpu_count, " +
            "status, jenkins_url, agent_name, work_dir, java_version, python_version, agent_version, " +
            "labels, description, deploy_log, created_by, updated_by" +
            ") VALUES (" +
            "#{uid}, #{name}, #{host}, #{port}, #{username}, #{encrypted_password}, #{os_type}, #{region}, " +
            "#{container_type}, #{resource_type}, #{resource_spec}, #{cpu_cores}, #{ram_gb}, #{gpu_memory}, #{gpu_model}, #{gpu_count}, "
            +
            "#{status}, #{jenkins_url}, #{agent_name}, #{work_dir}, #{java_version}, #{python_version}, #{agent_version}, "
            +
            "#{labels}, #{description}, #{deploy_log}, #{created_by}, #{updated_by}" +
            ")")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(JenkinsNode jenkinsNode);

    @Update("UPDATE xnet_aiops_clm_jenkins_node SET " +
            "name = #{name}, host = #{host}, port = #{port}, " +
            "username = #{username}, encrypted_password = #{encrypted_password}, " +
            "os_type = #{os_type}, region = #{region}, " +
            "container_type = #{container_type}, resource_type = #{resource_type}, " +
            "resource_spec = #{resource_spec}, cpu_cores = #{cpu_cores}, ram_gb = #{ram_gb}, " +
            "gpu_memory = #{gpu_memory}, gpu_model = #{gpu_model}, gpu_count = #{gpu_count}, " +
            "status = #{status}, jenkins_url = #{jenkins_url}, agent_name = #{agent_name}, " +
            "work_dir = #{work_dir}, java_version = #{java_version}, python_version = #{python_version}, " +
            "agent_version = #{agent_version}, labels = #{labels}, description = #{description}, " +
            "deploy_log = #{deploy_log}, updated_by = #{updated_by}, " +
            "updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id}")
    int update(JenkinsNode jenkinsNode);

    @Update("UPDATE xnet_aiops_clm_jenkins_node SET " +
            "status = #{status}, deploy_log = #{deployLog}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("deployLog") String deployLog);

    @Update("UPDATE xnet_aiops_clm_jenkins_node SET " +
            "last_heartbeat = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateHeartbeat(Long id);

    @Delete("DELETE FROM xnet_aiops_clm_jenkins_node WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT COUNT(*) FROM xnet_aiops_clm_jenkins_node WHERE name = #{name} AND id != COALESCE(#{excludeId}, -1)")
    int countByName(@Param("name") String name, @Param("excludeId") Long excludeId);

    @Select("SELECT COUNT(*) FROM xnet_aiops_clm_jenkins_node WHERE host = #{host} AND port = #{port} AND id != COALESCE(#{excludeId}, -1)")
    int countByHostAndPort(@Param("host") String host, @Param("port") Integer port, @Param("excludeId") Long excludeId);
}
