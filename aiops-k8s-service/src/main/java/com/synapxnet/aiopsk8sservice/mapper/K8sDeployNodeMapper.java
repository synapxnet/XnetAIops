package com.synapxnet.aiopsk8sservice.mapper;

import com.synapxnet.aiopsk8sservice.entity.K8sDeployNode;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface K8sDeployNodeMapper {

    @Select("SELECT * FROM xnet_aiops_k8s_deploy_node WHERE plan_id = #{planId}")
    List<K8sDeployNode> findByPlanId(@Param("planId") Long planId);

    @Select("SELECT * FROM xnet_aiops_k8s_deploy_node WHERE id = #{id}")
    K8sDeployNode findById(@Param("id") Long id);

    @Insert("INSERT INTO xnet_aiops_k8s_deploy_node (plan_id, host, ssh_port, ssh_user, ssh_password, ssh_key, role, hostname, status) " +
            "VALUES (#{planId}, #{host}, #{sshPort}, #{sshUser}, #{sshPassword}, #{sshKey}, #{role}, #{hostname}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(K8sDeployNode node);

    @Update("UPDATE xnet_aiops_k8s_deploy_node SET status=#{status}, status_message=#{statusMessage} WHERE id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("statusMessage") String statusMessage);

    @Delete("DELETE FROM xnet_aiops_k8s_deploy_node WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
