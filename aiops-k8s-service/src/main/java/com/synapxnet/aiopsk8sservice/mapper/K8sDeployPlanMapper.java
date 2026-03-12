package com.synapxnet.aiopsk8sservice.mapper;

import com.synapxnet.aiopsk8sservice.entity.K8sDeployPlan;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface K8sDeployPlanMapper {

    @Select("SELECT * FROM xnet_aiops_k8s_deploy_plan ORDER BY created_at DESC")
    List<K8sDeployPlan> findAll();

    @Select("SELECT * FROM xnet_aiops_k8s_deploy_plan WHERE id = #{id}")
    K8sDeployPlan findById(@Param("id") Long id);

    @Insert("INSERT INTO xnet_aiops_k8s_deploy_plan (uid, plan_name, k8s_version, deploy_type, network_plugin, container_runtime, pod_cidr, service_cidr, install_metrics_server, install_ingress_nginx, storage_plugin, registry_url, status) " +
            "VALUES (#{uid}, #{planName}, #{k8sVersion}, #{deployType}, #{networkPlugin}, #{containerRuntime}, #{podCidr}, #{serviceCidr}, #{installMetricsServer}, #{installIngressNginx}, #{storagePlugin}, #{registryUrl}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(K8sDeployPlan plan);

    @Update("UPDATE xnet_aiops_k8s_deploy_plan SET status=#{status}, result_cluster_id=#{resultClusterId} WHERE id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("resultClusterId") Long resultClusterId);

    @Delete("DELETE FROM xnet_aiops_k8s_deploy_plan WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
