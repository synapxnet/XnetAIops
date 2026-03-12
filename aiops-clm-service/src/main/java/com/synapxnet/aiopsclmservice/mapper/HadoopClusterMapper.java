package com.synapxnet.aiopsclmservice.mapper;

import com.synapxnet.aiopsclmservice.entity.HadoopCluster;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

/**
 * Hadoop 集群 Mapper
 */
@Mapper
public interface HadoopClusterMapper {

    @Select("SELECT * FROM xnet_aiops_clm_hadoop_cluster ORDER BY created_at DESC")
    List<HadoopCluster> findAll();

    @Select("SELECT * FROM xnet_aiops_clm_hadoop_cluster WHERE id = #{id}")
    Optional<HadoopCluster> findById(Long id);

    @Select("SELECT * FROM xnet_aiops_clm_hadoop_cluster WHERE uid = #{uid}")
    Optional<HadoopCluster> findByUid(String uid);

    @Select("SELECT * FROM xnet_aiops_clm_hadoop_cluster WHERE node_type = #{nodeType} ORDER BY created_at DESC")
    List<HadoopCluster> findByNodeType(@Param("nodeType") String nodeType);

    @Select("SELECT * FROM xnet_aiops_clm_hadoop_cluster WHERE node_type = 'master' ORDER BY created_at DESC")
    List<HadoopCluster> findMasters();

    @Select("SELECT * FROM xnet_aiops_clm_hadoop_cluster WHERE node_type = 'node' ORDER BY created_at DESC")
    List<HadoopCluster> findNodes();

    @Select("SELECT * FROM xnet_aiops_clm_hadoop_cluster WHERE master_id = #{masterId} ORDER BY created_at DESC")
    List<HadoopCluster> findByMasterId(@Param("masterId") Long masterId);

    @Select("SELECT * FROM xnet_aiops_clm_hadoop_cluster WHERE status = #{status} ORDER BY created_at DESC")
    List<HadoopCluster> findByStatus(@Param("status") String status);

    @Select("SELECT * FROM xnet_aiops_clm_hadoop_cluster WHERE status IN ('deployed', 'running') ORDER BY created_at DESC")
    List<HadoopCluster> findDeployedClusters();

    @Insert("INSERT INTO xnet_aiops_clm_hadoop_cluster (" +
            "uid, name, description, host, port, ssh_user, ssh_password, ssh_private_key, " +
            "hadoop_version, os_type, node_type, deploy_mode, components, " +
            "hdfs_data_dirs, hdfs_replication, hdfs_block_size, " +
            "yarn_memory, yarn_cpu, ha_master_host, zk_cluster, " +
            "status, deploy_log, master_id, created_by" +
            ") VALUES (" +
            "#{uid}, #{name}, #{description}, #{host}, #{port}, #{sshUser}, #{sshPassword}, #{sshPrivateKey}, " +
            "#{hadoopVersion}, #{osType}, #{nodeType}, #{deployMode}, #{components}, " +
            "#{hdfsDataDirs}, #{hdfsReplication}, #{hdfsBlockSize}, " +
            "#{yarnMemory}, #{yarnCpu}, #{haMasterHost}, #{zkCluster}, " +
            "#{status}, #{deployLog}, #{masterId}, #{createdBy}" +
            ")")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(HadoopCluster hadoopCluster);

    @Update("UPDATE xnet_aiops_clm_hadoop_cluster SET " +
            "name = #{name}, description = #{description}, host = #{host}, port = #{port}, " +
            "ssh_user = #{sshUser}, ssh_password = #{sshPassword}, ssh_private_key = #{sshPrivateKey}, " +
            "hadoop_version = #{hadoopVersion}, os_type = #{osType}, deploy_mode = #{deployMode}, " +
            "components = #{components}, hdfs_data_dirs = #{hdfsDataDirs}, " +
            "hdfs_replication = #{hdfsReplication}, hdfs_block_size = #{hdfsBlockSize}, " +
            "yarn_memory = #{yarnMemory}, yarn_cpu = #{yarnCpu}, " +
            "ha_master_host = #{haMasterHost}, zk_cluster = #{zkCluster}, " +
            "status = #{status}, deploy_log = #{deployLog}, master_id = #{masterId}, " +
            "updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id}")
    int update(HadoopCluster hadoopCluster);

    @Update("UPDATE xnet_aiops_clm_hadoop_cluster SET " +
            "status = #{status}, deploy_log = #{deployLog}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("deployLog") String deployLog);

    @Update("UPDATE xnet_aiops_clm_hadoop_cluster SET " +
            "status = #{status}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id}")
    int updateStatusOnly(@Param("id") Long id, @Param("status") String status);

    @Delete("DELETE FROM xnet_aiops_clm_hadoop_cluster WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT COUNT(*) FROM xnet_aiops_clm_hadoop_cluster WHERE name = #{name} AND (#{excludeId} IS NULL OR id != #{excludeId})")
    int countByName(@Param("name") String name, @Param("excludeId") Long excludeId);

    @Select("SELECT COUNT(*) FROM xnet_aiops_clm_hadoop_cluster WHERE host = #{host} AND (#{excludeId} IS NULL OR id != #{excludeId})")
    int countByHost(@Param("host") String host, @Param("excludeId") Long excludeId);

    @Select("SELECT COUNT(*) FROM xnet_aiops_clm_hadoop_cluster WHERE node_type = #{nodeType}")
    int countByNodeType(@Param("nodeType") String nodeType);

    @Select("SELECT COUNT(*) FROM xnet_aiops_clm_hadoop_cluster WHERE status = #{status}")
    int countByStatus(@Param("status") String status);
}
