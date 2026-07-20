package com.synapxnet.aiopsk8sservice.mapper;

import com.synapxnet.aiopsk8sservice.entity.K8sPrometheusConfig;
import org.apache.ibatis.annotations.*;

@Mapper
public interface K8sPrometheusConfigMapper {

    @Select("SELECT * FROM xnet_aiops_k8s_prometheus_config WHERE cluster_id = #{clusterId}")
    K8sPrometheusConfig findByClusterId(@Param("clusterId") Long clusterId);

    @Insert("INSERT INTO xnet_aiops_k8s_prometheus_config (cluster_id, prometheus_url, auth_type, auth_token, username, password, status) " +
            "VALUES (#{clusterId}, #{prometheusUrl}, #{authType}, #{authToken}, #{username}, #{password}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(K8sPrometheusConfig config);

    @Update("UPDATE xnet_aiops_k8s_prometheus_config SET prometheus_url=#{prometheusUrl}, auth_type=#{authType}, " +
            "auth_token=#{authToken}, username=#{username}, password=#{password}, status=#{status} WHERE cluster_id=#{clusterId}")
    int updateByClusterId(K8sPrometheusConfig config);

    @Delete("DELETE FROM xnet_aiops_k8s_prometheus_config WHERE cluster_id = #{clusterId}")
    int deleteByClusterId(@Param("clusterId") Long clusterId);
}
