package com.synapxnet.aiopsk8sservice.mapper;

import com.synapxnet.aiopsk8sservice.entity.K8sPipelineRun;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface K8sPipelineRunMapper {

    @Select("SELECT * FROM xnet_aiops_k8s_pipeline_run WHERE pipeline_id = #{pipelineId} ORDER BY created_at DESC")
    List<K8sPipelineRun> findByPipelineId(@Param("pipelineId") Long pipelineId);

    @Select("SELECT * FROM xnet_aiops_k8s_pipeline_run WHERE id = #{id}")
    K8sPipelineRun findById(@Param("id") Long id);

    @Insert("INSERT INTO xnet_aiops_k8s_pipeline_run (pipeline_id, run_number, status, trigger_type, " +
            "trigger_user, parameters, jenkins_build_url) " +
            "VALUES (#{pipelineId}, #{runNumber}, #{status}, #{triggerType}, " +
            "#{triggerUser}, #{parameters}, #{jenkinsBuildUrl})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(K8sPipelineRun run);

    @Update("UPDATE xnet_aiops_k8s_pipeline_run SET status=#{status}, start_time=#{startTime}, " +
            "end_time=#{endTime}, duration_ms=#{durationMs}, stages_status=#{stagesStatus} WHERE id=#{id}")
    int updateStatus(K8sPipelineRun run);

    @Update("UPDATE xnet_aiops_k8s_pipeline_run SET log_text=#{logText} WHERE id=#{id}")
    int updateLog(@Param("id") Long id, @Param("logText") String logText);

    @Select("SELECT MAX(run_number) FROM xnet_aiops_k8s_pipeline_run WHERE pipeline_id = #{pipelineId}")
    Integer getMaxRunNumber(@Param("pipelineId") Long pipelineId);
}
