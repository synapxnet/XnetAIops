package com.synapxnet.aiopsk8sservice.mapper;

import com.synapxnet.aiopsk8sservice.entity.K8sDeployLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface K8sDeployLogMapper {

    @Select("SELECT * FROM xnet_aiops_k8s_deploy_log WHERE plan_id = #{planId} ORDER BY created_at ASC")
    List<K8sDeployLog> findByPlanId(@Param("planId") Long planId);

    @Insert("INSERT INTO xnet_aiops_k8s_deploy_log (plan_id, node_host, step, log_level, message) " +
            "VALUES (#{planId}, #{nodeHost}, #{step}, #{logLevel}, #{message})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(K8sDeployLog log);
}
