package com.synapxnet.aiopsmonservice.mapper;

import com.synapxnet.aiopsmonservice.entity.NotifyGroup;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface NotifyGroupMapper {

    @Select("SELECT * FROM xnet_aiops_mon_notify_group ORDER BY created_at DESC")
    List<NotifyGroup> findAll();

    @Select("SELECT * FROM xnet_aiops_mon_notify_group WHERE id = #{id}")
    NotifyGroup findById(@Param("id") Long id);

    @Insert("INSERT INTO xnet_aiops_mon_notify_group (group_name, notify_type, webhook_url) " +
            "VALUES (#{groupName}, #{notifyType}, #{webhookUrl})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NotifyGroup notifyGroup);

    @Update("UPDATE xnet_aiops_mon_notify_group SET group_name=#{groupName}, notify_type=#{notifyType}, " +
            "webhook_url=#{webhookUrl} WHERE id=#{id}")
    int update(NotifyGroup notifyGroup);

    @Delete("DELETE FROM xnet_aiops_mon_notify_group WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
