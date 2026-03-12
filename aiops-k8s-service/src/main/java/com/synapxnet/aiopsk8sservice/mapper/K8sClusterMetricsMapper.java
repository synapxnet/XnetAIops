package com.synapxnet.aiopsk8sservice.mapper;

import com.synapxnet.aiopsk8sservice.entity.K8sClusterMetricsSnapshot;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface K8sClusterMetricsMapper {

    @Select("SELECT * FROM xnet_aiops_k8s_cluster_metrics_snapshot WHERE cluster_id = #{clusterId} ORDER BY snapshot_time DESC LIMIT #{limit}")
    List<K8sClusterMetricsSnapshot> findByClusterId(@Param("clusterId") Long clusterId, @Param("limit") int limit);

    @Select("SELECT * FROM xnet_aiops_k8s_cluster_metrics_snapshot WHERE cluster_id = #{clusterId} ORDER BY snapshot_time DESC LIMIT 1")
    K8sClusterMetricsSnapshot findLatestByClusterId(@Param("clusterId") Long clusterId);

    @Insert("INSERT INTO xnet_aiops_k8s_cluster_metrics_snapshot (cluster_id, cpu_capacity, cpu_used, memory_capacity, memory_used, pod_capacity, pod_used, storage_capacity, storage_used) " +
            "VALUES (#{clusterId}, #{cpuCapacity}, #{cpuUsed}, #{memoryCapacity}, #{memoryUsed}, #{podCapacity}, #{podUsed}, #{storageCapacity}, #{storageUsed})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(K8sClusterMetricsSnapshot snapshot);

    @Delete("DELETE FROM xnet_aiops_k8s_cluster_metrics_snapshot WHERE cluster_id = #{clusterId} AND snapshot_time < DATE_SUB(NOW(), INTERVAL #{days} DAY)")
    int deleteOlderThan(@Param("clusterId") Long clusterId, @Param("days") int days);
}
