package com.synapxnet.aiopsmonservice.mapper;

import com.synapxnet.aiopsmonservice.entity.AlertHistory;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AlertHistoryMapper {

    @Select("SELECT * FROM xnet_aiops_mon_alert_history WHERE cluster_id = #{clusterId} ORDER BY triggered_at DESC")
    List<AlertHistory> findByClusterId(@Param("clusterId") Long clusterId);

    @Select("SELECT * FROM xnet_aiops_mon_alert_history WHERE cluster_id = #{clusterId} AND status = #{status} ORDER BY triggered_at DESC")
    List<AlertHistory> findByClusterIdAndStatus(@Param("clusterId") Long clusterId, @Param("status") String status);

    @Select("SELECT * FROM xnet_aiops_mon_alert_history ORDER BY triggered_at DESC")
    List<AlertHistory> findAll();

    @Select("SELECT * FROM xnet_aiops_mon_alert_history WHERE id = #{id}")
    AlertHistory findById(@Param("id") Long id);

    /**
     * 根据稳定 UID 获取告警记录，供 Agent 证据工具直接定位领域事实。
     *
     * @param uid 告警稳定 UID
     * @return 告警记录，不存在时返回 null
     */
    @Select("SELECT * FROM xnet_aiops_mon_alert_history WHERE uid = #{uid}")
    AlertHistory findByUid(@Param("uid") String uid);

    @Insert("INSERT INTO xnet_aiops_mon_alert_history (uid, cluster_id, alert_rule_id, alert_name, hostname, " +
            "alert_level, alert_info, alert_advice, status) " +
            "VALUES (#{uid}, #{clusterId}, #{alertRuleId}, #{alertName}, #{hostname}, " +
            "#{alertLevel}, #{alertInfo}, #{alertAdvice}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AlertHistory alertHistory);

    @Update("UPDATE xnet_aiops_mon_alert_history SET status=#{status}, resolved_at=#{resolvedAt} WHERE id=#{id}")
    int updateStatus(AlertHistory alertHistory);

    @Select("SELECT COUNT(*) FROM xnet_aiops_mon_alert_history WHERE status = 'open'")
    int countOpen();

    @Select("SELECT COUNT(*) FROM xnet_aiops_mon_alert_history WHERE status = 'open' AND alert_level = 'critical'")
    int countOpenCritical();

    @Select("SELECT COUNT(*) FROM xnet_aiops_mon_alert_history")
    int countAll();
}
