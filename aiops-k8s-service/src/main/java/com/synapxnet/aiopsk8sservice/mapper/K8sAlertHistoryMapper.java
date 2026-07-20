package com.synapxnet.aiopsk8sservice.mapper;

import com.synapxnet.aiopsk8sservice.entity.K8sAlertHistory;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface K8sAlertHistoryMapper {

    @Select("SELECT * FROM xnet_aiops_k8s_alert_history WHERE cluster_id = #{clusterId} ORDER BY created_at DESC LIMIT #{limit}")
    List<K8sAlertHistory> findByClusterId(@Param("clusterId") Long clusterId, @Param("limit") int limit);

    @Select("SELECT * FROM xnet_aiops_k8s_alert_history WHERE rule_id = #{ruleId} ORDER BY created_at DESC LIMIT #{limit}")
    List<K8sAlertHistory> findByRuleId(@Param("ruleId") Long ruleId, @Param("limit") int limit);

    @Insert("INSERT INTO xnet_aiops_k8s_alert_history (rule_id, cluster_id, rule_name, severity, resource_type, resource_name, message, status, current_value, threshold, fired_at) " +
            "VALUES (#{ruleId}, #{clusterId}, #{ruleName}, #{severity}, #{resourceType}, #{resourceName}, #{message}, #{status}, #{currentValue}, #{threshold}, #{firedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(K8sAlertHistory history);

    @Update("UPDATE xnet_aiops_k8s_alert_history SET status='resolved', resolved_at=NOW() WHERE id=#{id}")
    int resolve(@Param("id") Long id);

    @Select("SELECT * FROM xnet_aiops_k8s_alert_history WHERE id = #{id}")
    K8sAlertHistory findById(@Param("id") Long id);

    @Delete("DELETE FROM xnet_aiops_k8s_alert_history WHERE cluster_id = #{clusterId}")
    int deleteByClusterId(@Param("clusterId") Long clusterId);
}
