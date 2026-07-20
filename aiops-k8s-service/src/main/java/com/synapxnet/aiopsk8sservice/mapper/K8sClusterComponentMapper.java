package com.synapxnet.aiopsk8sservice.mapper;

import com.synapxnet.aiopsk8sservice.entity.K8sClusterComponent;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface K8sClusterComponentMapper {

    @Select("SELECT * FROM xnet_aiops_k8s_cluster_component WHERE cluster_id = #{clusterId}")
    List<K8sClusterComponent> findByClusterId(@Param("clusterId") Long clusterId);

    @Insert("INSERT INTO xnet_aiops_k8s_cluster_component (cluster_id, component_name, component_type, status, message, checked_at) " +
            "VALUES (#{clusterId}, #{componentName}, #{componentType}, #{status}, #{message}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(K8sClusterComponent component);

    @Delete("DELETE FROM xnet_aiops_k8s_cluster_component WHERE cluster_id = #{clusterId}")
    int deleteByClusterId(@Param("clusterId") Long clusterId);
}
