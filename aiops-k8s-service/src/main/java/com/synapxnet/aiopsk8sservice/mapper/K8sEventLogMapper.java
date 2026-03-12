package com.synapxnet.aiopsk8sservice.mapper;

import com.synapxnet.aiopsk8sservice.entity.K8sEventLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface K8sEventLogMapper {

    @Select("SELECT * FROM xnet_aiops_k8s_event_log WHERE cluster_id = #{clusterId} ORDER BY last_timestamp DESC LIMIT #{limit}")
    List<K8sEventLog> findByClusterId(@Param("clusterId") Long clusterId, @Param("limit") int limit);

    @Select("SELECT * FROM xnet_aiops_k8s_event_log WHERE cluster_id = #{clusterId} AND namespace = #{namespace} ORDER BY last_timestamp DESC LIMIT #{limit}")
    List<K8sEventLog> findByClusterIdAndNamespace(@Param("clusterId") Long clusterId, @Param("namespace") String namespace, @Param("limit") int limit);

    @Insert("INSERT INTO xnet_aiops_k8s_event_log (cluster_id, namespace, kind, name, event_type, reason, message, source_component, first_timestamp, last_timestamp, count) " +
            "VALUES (#{clusterId}, #{namespace}, #{kind}, #{name}, #{eventType}, #{reason}, #{message}, #{sourceComponent}, #{firstTimestamp}, #{lastTimestamp}, #{count})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(K8sEventLog event);

    @Delete("DELETE FROM xnet_aiops_k8s_event_log WHERE cluster_id = #{clusterId} AND created_at < DATE_SUB(NOW(), INTERVAL #{days} DAY)")
    int deleteOlderThan(@Param("clusterId") Long clusterId, @Param("days") int days);
}
