package com.synapxnet.aiopsclmservice.mapper;

import com.synapxnet.aiopsclmservice.entity.Cluster;
import com.synapxnet.aiopsclmservice.entity.ClusterVariable;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ClusterMapper {

    @Select("SELECT * FROM xnet_aiops_clm_cluster ORDER BY created_at DESC")
    List<Cluster> findAll();

    @Select("SELECT * FROM xnet_aiops_clm_cluster WHERE id = #{id}")
    Cluster findById(@Param("id") Long id);

    @Select("SELECT * FROM xnet_aiops_clm_cluster WHERE uid = #{uid}")
    Cluster findByUid(@Param("uid") String uid);

    @Insert("INSERT INTO xnet_aiops_clm_cluster (uid, cluster_name, cluster_code, description, cluster_type, status, created_by) " +
            "VALUES (#{uid}, #{clusterName}, #{clusterCode}, #{description}, #{clusterType}, #{status}, #{createdBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Cluster cluster);

    @Update("UPDATE xnet_aiops_clm_cluster SET cluster_name=#{clusterName}, description=#{description}, " +
            "cluster_type=#{clusterType}, status=#{status} WHERE id=#{id}")
    int update(Cluster cluster);

    @Update("UPDATE xnet_aiops_clm_cluster SET total_hosts=#{totalHosts}, running_services=#{runningServices} WHERE id=#{id}")
    int updateStats(@Param("id") Long id, @Param("totalHosts") int totalHosts, @Param("runningServices") int runningServices);

    @Delete("DELETE FROM xnet_aiops_clm_cluster WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    // --- ClusterVariable ---

    @Select("SELECT * FROM xnet_aiops_clm_variable WHERE cluster_id = #{clusterId}")
    List<ClusterVariable> findVariablesByClusterId(@Param("clusterId") Long clusterId);

    @Insert("INSERT INTO xnet_aiops_clm_variable (cluster_id, variable_name, variable_value) " +
            "VALUES (#{clusterId}, #{variableName}, #{variableValue}) " +
            "ON DUPLICATE KEY UPDATE variable_value = #{variableValue}")
    int upsertVariable(ClusterVariable variable);

    @Delete("DELETE FROM xnet_aiops_clm_variable WHERE id = #{id}")
    int deleteVariable(@Param("id") Long id);
}
