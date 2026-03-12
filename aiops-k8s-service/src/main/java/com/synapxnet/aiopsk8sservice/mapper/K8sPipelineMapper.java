package com.synapxnet.aiopsk8sservice.mapper;

import com.synapxnet.aiopsk8sservice.entity.K8sPipeline;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface K8sPipelineMapper {

    @Select("SELECT * FROM xnet_aiops_k8s_pipeline WHERE project_id = #{projectId} ORDER BY created_at DESC")
    List<K8sPipeline> findByProjectId(@Param("projectId") Long projectId);

    @Select("SELECT * FROM xnet_aiops_k8s_pipeline WHERE id = #{id}")
    K8sPipeline findById(@Param("id") Long id);

    @Insert("INSERT INTO xnet_aiops_k8s_pipeline (project_id, name, description, type, jenkinsfile, " +
            "source_type, source_url, source_branch, credential_id, disable_concurrent, " +
            "timer_trigger, jenkins_job_name, jenkins_job_path, status) " +
            "VALUES (#{projectId}, #{name}, #{description}, #{type}, #{jenkinsfile}, " +
            "#{sourceType}, #{sourceUrl}, #{sourceBranch}, #{credentialId}, #{disableConcurrent}, " +
            "#{timerTrigger}, #{jenkinsJobName}, #{jenkinsJobPath}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(K8sPipeline pipeline);

    @Update("UPDATE xnet_aiops_k8s_pipeline SET name=#{name}, description=#{description}, type=#{type}, " +
            "jenkinsfile=#{jenkinsfile}, source_type=#{sourceType}, source_url=#{sourceUrl}, " +
            "source_branch=#{sourceBranch}, credential_id=#{credentialId}, " +
            "disable_concurrent=#{disableConcurrent}, timer_trigger=#{timerTrigger}, " +
            "jenkins_job_name=#{jenkinsJobName}, jenkins_job_path=#{jenkinsJobPath}, status=#{status} " +
            "WHERE id=#{id}")
    int update(K8sPipeline pipeline);

    @Update("UPDATE xnet_aiops_k8s_pipeline SET last_run_status=#{status}, last_run_time=#{time} WHERE id=#{id}")
    int updateLastRun(@Param("id") Long id, @Param("status") String status, @Param("time") LocalDateTime time);

    @Delete("DELETE FROM xnet_aiops_k8s_pipeline WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
