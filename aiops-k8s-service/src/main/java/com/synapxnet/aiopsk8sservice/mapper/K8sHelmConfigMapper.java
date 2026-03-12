package com.synapxnet.aiopsk8sservice.mapper;

import com.synapxnet.aiopsk8sservice.entity.K8sHelmConfig;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface K8sHelmConfigMapper {

    @Select("SELECT * FROM xnet_aiops_k8s_helm_config ORDER BY id")
    List<K8sHelmConfig> findAll();

    @Select("SELECT * FROM xnet_aiops_k8s_helm_config WHERE config_key = #{key}")
    K8sHelmConfig findByKey(@Param("key") String key);

    @Insert("INSERT INTO xnet_aiops_k8s_helm_config (config_key, config_value, description) " +
            "VALUES (#{configKey}, #{configValue}, #{description}) " +
            "ON DUPLICATE KEY UPDATE config_value = #{configValue}, description = #{description}")
    int upsert(K8sHelmConfig config);
}
