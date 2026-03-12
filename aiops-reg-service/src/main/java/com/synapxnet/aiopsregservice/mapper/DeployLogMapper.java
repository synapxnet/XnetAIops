package com.synapxnet.aiopsregservice.mapper;

import com.synapxnet.aiopsregservice.entity.DeployLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DeployLogMapper {

    @Select("SELECT * FROM xnet_aiops_reg_deploy_log WHERE registry_id=#{registryId} ORDER BY started_at DESC")
    List<DeployLog> findByRegistryId(Long registryId);

    @Select("SELECT * FROM xnet_aiops_reg_deploy_log WHERE id=#{id}")
    DeployLog findById(Long id);

    @Insert("INSERT INTO xnet_aiops_reg_deploy_log (registry_id, action, status, log_text, started_at, finished_at) " +
            "VALUES (#{registryId}, #{action}, #{status}, #{logText}, #{startedAt}, #{finishedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(DeployLog log);

    @Update("UPDATE xnet_aiops_reg_deploy_log SET status=#{status}, log_text=#{logText}, finished_at=#{finishedAt} WHERE id=#{id}")
    void update(DeployLog log);
}
