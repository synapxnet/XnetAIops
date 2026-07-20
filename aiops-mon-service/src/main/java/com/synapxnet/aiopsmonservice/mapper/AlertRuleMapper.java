package com.synapxnet.aiopsmonservice.mapper;

import com.synapxnet.aiopsmonservice.entity.AlertRule;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AlertRuleMapper {

    @Select("SELECT * FROM xnet_aiops_mon_alert_rule WHERE cluster_id = #{clusterId} ORDER BY created_at DESC")
    List<AlertRule> findByClusterId(@Param("clusterId") Long clusterId);

    @Select("SELECT * FROM xnet_aiops_mon_alert_rule ORDER BY created_at DESC")
    List<AlertRule> findAll();

    @Select("SELECT * FROM xnet_aiops_mon_alert_rule WHERE id = #{id}")
    AlertRule findById(@Param("id") Long id);

    @Insert("INSERT INTO xnet_aiops_mon_alert_rule (uid, cluster_id, rule_name, service_name, expression, " +
            "compare_method, threshold_value, alert_level, duration_seconds, enabled, description) " +
            "VALUES (#{uid}, #{clusterId}, #{ruleName}, #{serviceName}, #{expression}, " +
            "#{compareMethod}, #{thresholdValue}, #{alertLevel}, #{durationSeconds}, #{enabled}, #{description})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AlertRule alertRule);

    @Update("UPDATE xnet_aiops_mon_alert_rule SET rule_name=#{ruleName}, service_name=#{serviceName}, " +
            "expression=#{expression}, compare_method=#{compareMethod}, threshold_value=#{thresholdValue}, " +
            "alert_level=#{alertLevel}, duration_seconds=#{durationSeconds}, enabled=#{enabled}, " +
            "description=#{description} WHERE id=#{id}")
    int update(AlertRule alertRule);

    @Update("UPDATE xnet_aiops_mon_alert_rule SET enabled=#{enabled} WHERE id=#{id}")
    int updateEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled);

    @Delete("DELETE FROM xnet_aiops_mon_alert_rule WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
