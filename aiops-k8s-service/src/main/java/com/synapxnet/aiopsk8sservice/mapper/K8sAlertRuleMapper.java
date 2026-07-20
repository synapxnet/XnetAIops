package com.synapxnet.aiopsk8sservice.mapper;

import com.synapxnet.aiopsk8sservice.entity.K8sAlertRule;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface K8sAlertRuleMapper {

    @Select("SELECT * FROM xnet_aiops_k8s_alert_rule WHERE cluster_id = #{clusterId} ORDER BY created_at DESC")
    List<K8sAlertRule> findByClusterId(@Param("clusterId") Long clusterId);

    @Select("SELECT * FROM xnet_aiops_k8s_alert_rule WHERE id = #{id}")
    K8sAlertRule findById(@Param("id") Long id);

    @Insert("INSERT INTO xnet_aiops_k8s_alert_rule (cluster_id, name, description, severity, resource_type, metric_name, `condition`, threshold, duration, enabled, notify_channels) " +
            "VALUES (#{clusterId}, #{name}, #{description}, #{severity}, #{resourceType}, #{metricName}, #{condition}, #{threshold}, #{duration}, #{enabled}, #{notifyChannels})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(K8sAlertRule rule);

    @Update("UPDATE xnet_aiops_k8s_alert_rule SET name=#{name}, description=#{description}, severity=#{severity}, " +
            "resource_type=#{resourceType}, metric_name=#{metricName}, `condition`=#{condition}, threshold=#{threshold}, " +
            "duration=#{duration}, enabled=#{enabled}, notify_channels=#{notifyChannels} WHERE id=#{id}")
    int update(K8sAlertRule rule);

    @Delete("DELETE FROM xnet_aiops_k8s_alert_rule WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT * FROM xnet_aiops_k8s_alert_rule WHERE enabled = true AND cluster_id = #{clusterId}")
    List<K8sAlertRule> findEnabledByClusterId(@Param("clusterId") Long clusterId);
}
