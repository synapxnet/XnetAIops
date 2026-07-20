package com.synapxnet.aiopsclmservice.mapper;

import com.synapxnet.aiopsclmservice.entity.MySQLInstance;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * MySQL Instance Mapper
 */
@Mapper
public interface MySQLInstanceMapper {

    @Select("SELECT * FROM xnet_aiops_clm_mysql_instance ORDER BY created_at DESC")
    List<MySQLInstance> findAll();

    @Select("SELECT * FROM xnet_aiops_clm_mysql_instance WHERE id = #{id}")
    MySQLInstance findById(@Param("id") Long id);

    @Select("SELECT * FROM xnet_aiops_clm_mysql_instance WHERE uid = #{uid}")
    MySQLInstance findByUid(@Param("uid") String uid);

    @Select("SELECT * FROM xnet_aiops_clm_mysql_instance WHERE cluster_id = #{clusterId} ORDER BY created_at DESC")
    List<MySQLInstance> findByClusterId(@Param("clusterId") Long clusterId);

    @Select("SELECT * FROM xnet_aiops_clm_mysql_instance WHERE status = #{status} ORDER BY created_at DESC")
    List<MySQLInstance> findByStatus(@Param("status") String status);

    @Insert("INSERT INTO xnet_aiops_clm_mysql_instance (" +
            "uid, instance_name, cluster_id, host, ssh_port, ssh_user, auth_type, " +
            "encrypted_password, encrypted_private_key, " +
            "mysql_port, mysql_version, data_dir, charset, innodb_buffer_pool_size, max_connections, " +
            "encrypted_root_password, role, master_instance_id, server_id, " +
            "status, deploy_log, description, created_by" +
            ") VALUES (" +
            "#{uid}, #{instanceName}, #{clusterId}, #{host}, #{sshPort}, #{sshUser}, #{authType}, " +
            "#{encryptedPassword}, #{encryptedPrivateKey}, " +
            "#{mysqlPort}, #{mysqlVersion}, #{dataDir}, #{charset}, #{innodbBufferPoolSize}, #{maxConnections}, " +
            "#{encryptedRootPassword}, #{role}, #{masterInstanceId}, #{serverId}, " +
            "#{status}, #{deployLog}, #{description}, #{createdBy}" +
            ")")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MySQLInstance instance);

    @Update("UPDATE xnet_aiops_clm_mysql_instance SET " +
            "instance_name=#{instanceName}, cluster_id=#{clusterId}, host=#{host}, " +
            "ssh_port=#{sshPort}, ssh_user=#{sshUser}, auth_type=#{authType}, " +
            "encrypted_password=#{encryptedPassword}, encrypted_private_key=#{encryptedPrivateKey}, " +
            "mysql_port=#{mysqlPort}, mysql_version=#{mysqlVersion}, data_dir=#{dataDir}, " +
            "charset=#{charset}, innodb_buffer_pool_size=#{innodbBufferPoolSize}, " +
            "max_connections=#{maxConnections}, encrypted_root_password=#{encryptedRootPassword}, " +
            "role=#{role}, master_instance_id=#{masterInstanceId}, server_id=#{serverId}, " +
            "description=#{description} " +
            "WHERE id=#{id}")
    int update(MySQLInstance instance);

    @Update("UPDATE xnet_aiops_clm_mysql_instance SET status=#{status}, deploy_log=#{deployLog} WHERE id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("deployLog") String deployLog);

    @Update("UPDATE xnet_aiops_clm_mysql_instance SET last_heartbeat=CURRENT_TIMESTAMP WHERE id=#{id}")
    int updateHeartbeat(@Param("id") Long id);

    @Delete("DELETE FROM xnet_aiops_clm_mysql_instance WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
