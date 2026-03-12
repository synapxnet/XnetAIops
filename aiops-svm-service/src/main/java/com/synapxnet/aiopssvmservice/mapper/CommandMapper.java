package com.synapxnet.aiopssvmservice.mapper;

import com.synapxnet.aiopssvmservice.entity.Command;
import com.synapxnet.aiopssvmservice.entity.CommandHost;
import com.synapxnet.aiopssvmservice.entity.CommandHostRole;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CommandMapper {

    // --- Command ---

    @Select("SELECT * FROM xnet_aiops_svm_command WHERE cluster_id = #{clusterId} ORDER BY created_at DESC")
    List<Command> findByClusterId(@Param("clusterId") Long clusterId);

    @Select("SELECT * FROM xnet_aiops_svm_command ORDER BY created_at DESC")
    List<Command> findAll();

    @Select("SELECT * FROM xnet_aiops_svm_command WHERE id = #{id}")
    Command findById(@Param("id") Long id);

    @Insert("INSERT INTO xnet_aiops_svm_command (uid, cluster_id, command_name, command_type, status, progress, " +
            "service_instance_id, created_by) " +
            "VALUES (#{uid}, #{clusterId}, #{commandName}, #{commandType}, #{status}, #{progress}, " +
            "#{serviceInstanceId}, #{createdBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Command command);

    @Update("UPDATE xnet_aiops_svm_command SET status=#{status}, progress=#{progress}, " +
            "started_at=#{startedAt}, finished_at=#{finishedAt} WHERE id=#{id}")
    int update(Command command);

    // --- CommandHost ---

    @Select("SELECT * FROM xnet_aiops_svm_command_host WHERE command_id = #{commandId}")
    List<CommandHost> findHostsByCommandId(@Param("commandId") Long commandId);

    @Insert("INSERT INTO xnet_aiops_svm_command_host (command_id, host_id, hostname, status, progress) " +
            "VALUES (#{commandId}, #{hostId}, #{hostname}, #{status}, #{progress})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertHost(CommandHost commandHost);

    @Update("UPDATE xnet_aiops_svm_command_host SET status=#{status}, progress=#{progress}, result_msg=#{resultMsg} WHERE id=#{id}")
    int updateHost(CommandHost commandHost);

    // --- Command query by service instance ---

    @Select("SELECT * FROM xnet_aiops_svm_command WHERE service_instance_id = #{serviceInstanceId} ORDER BY created_at DESC")
    List<Command> findByServiceInstanceId(@Param("serviceInstanceId") Long serviceInstanceId);

    // --- CommandHostRole ---

    @Select("SELECT * FROM xnet_aiops_svm_command_host_role WHERE command_host_id = #{commandHostId}")
    List<CommandHostRole> findRolesByCommandHostId(@Param("commandHostId") Long commandHostId);

    @Insert("INSERT INTO xnet_aiops_svm_command_host_role (command_host_id, role_name, role_type, status) " +
            "VALUES (#{commandHostId}, #{roleName}, #{roleType}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertHostRole(CommandHostRole role);

    @Update("UPDATE xnet_aiops_svm_command_host_role SET status=#{status}, result_msg=#{resultMsg}, " +
            "started_at=#{startedAt}, finished_at=#{finishedAt} WHERE id=#{id}")
    int updateHostRole(CommandHostRole role);
}
