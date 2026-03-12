package com.synapxnet.aiopsregservice.mapper;

import com.synapxnet.aiopsregservice.entity.SyncTask;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SyncTaskMapper {

    @Select("SELECT * FROM xnet_aiops_reg_sync_task WHERE registry_id = #{registryId} ORDER BY created_at DESC")
    List<SyncTask> findByRegistryId(@Param("registryId") Long registryId);

    @Select("SELECT * FROM xnet_aiops_reg_sync_task WHERE id = #{id}")
    SyncTask findById(@Param("id") Long id);

    @Select("SELECT * FROM xnet_aiops_reg_sync_task WHERE status = 'running' AND sync_method = 'harbor_replication'")
    List<SyncTask> findRunningHarborTasks();

    @Insert("INSERT INTO xnet_aiops_reg_sync_task (registry_id, source_image, target_project, sync_method, " +
            "harbor_policy_id, harbor_execution_id, status, status_detail, created_by) " +
            "VALUES (#{registryId}, #{sourceImage}, #{targetProject}, #{syncMethod}, " +
            "#{harborPolicyId}, #{harborExecutionId}, #{status}, #{statusDetail}, #{createdBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(SyncTask task);

    @Update("UPDATE xnet_aiops_reg_sync_task SET status = #{status}, status_detail = #{statusDetail}, " +
            "harbor_policy_id = #{harborPolicyId}, harbor_execution_id = #{harborExecutionId} WHERE id = #{id}")
    void update(SyncTask task);

    @Delete("DELETE FROM xnet_aiops_reg_sync_task WHERE id = #{id}")
    void deleteById(@Param("id") Long id);
}
