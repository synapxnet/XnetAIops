package com.synapxnet.aiopsclmservice.mapper;

import com.synapxnet.aiopsclmservice.entity.RedisInstance;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Redis Instance Mapper
 */
@Mapper
public interface RedisInstanceMapper {

    @Select("SELECT * FROM xnet_aiops_clm_redis_instance ORDER BY created_at DESC")
    List<RedisInstance> findAll();

    @Select("SELECT * FROM xnet_aiops_clm_redis_instance WHERE id = #{id}")
    RedisInstance findById(@Param("id") Long id);

    @Select("SELECT * FROM xnet_aiops_clm_redis_instance WHERE uid = #{uid}")
    RedisInstance findByUid(@Param("uid") String uid);

    @Select("SELECT * FROM xnet_aiops_clm_redis_instance WHERE cluster_id = #{clusterId} ORDER BY created_at DESC")
    List<RedisInstance> findByClusterId(@Param("clusterId") Long clusterId);

    @Select("SELECT * FROM xnet_aiops_clm_redis_instance WHERE status = #{status} ORDER BY created_at DESC")
    List<RedisInstance> findByStatus(@Param("status") String status);

    @Insert("INSERT INTO xnet_aiops_clm_redis_instance (" +
            "uid, instance_name, cluster_id, host, ssh_port, ssh_user, auth_type, " +
            "encrypted_password, encrypted_private_key, " +
            "redis_port, redis_version, encrypted_redis_password, " +
            "max_memory, max_memory_policy, persistence_mode, data_dir, " +
            "deploy_mode, role, master_instance_id, cluster_bus_port, " +
            "status, deploy_log, description, created_by" +
            ") VALUES (" +
            "#{uid}, #{instanceName}, #{clusterId}, #{host}, #{sshPort}, #{sshUser}, #{authType}, " +
            "#{encryptedPassword}, #{encryptedPrivateKey}, " +
            "#{redisPort}, #{redisVersion}, #{encryptedRedisPassword}, " +
            "#{maxMemory}, #{maxMemoryPolicy}, #{persistenceMode}, #{dataDir}, " +
            "#{deployMode}, #{role}, #{masterInstanceId}, #{clusterBusPort}, " +
            "#{status}, #{deployLog}, #{description}, #{createdBy}" +
            ")")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RedisInstance instance);

    @Update("UPDATE xnet_aiops_clm_redis_instance SET " +
            "instance_name=#{instanceName}, cluster_id=#{clusterId}, host=#{host}, " +
            "ssh_port=#{sshPort}, ssh_user=#{sshUser}, auth_type=#{authType}, " +
            "encrypted_password=#{encryptedPassword}, encrypted_private_key=#{encryptedPrivateKey}, " +
            "redis_port=#{redisPort}, redis_version=#{redisVersion}, " +
            "encrypted_redis_password=#{encryptedRedisPassword}, " +
            "max_memory=#{maxMemory}, max_memory_policy=#{maxMemoryPolicy}, " +
            "persistence_mode=#{persistenceMode}, data_dir=#{dataDir}, " +
            "deploy_mode=#{deployMode}, role=#{role}, master_instance_id=#{masterInstanceId}, " +
            "cluster_bus_port=#{clusterBusPort}, description=#{description} " +
            "WHERE id=#{id}")
    int update(RedisInstance instance);

    @Update("UPDATE xnet_aiops_clm_redis_instance SET status=#{status}, deploy_log=#{deployLog} WHERE id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("deployLog") String deployLog);

    @Update("UPDATE xnet_aiops_clm_redis_instance SET last_heartbeat=CURRENT_TIMESTAMP WHERE id=#{id}")
    int updateHeartbeat(@Param("id") Long id);

    @Delete("DELETE FROM xnet_aiops_clm_redis_instance WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
