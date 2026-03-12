package com.synapxnet.aiopshomservice.mapper;

import com.synapxnet.aiopshomservice.entity.Host;
import com.synapxnet.aiopshomservice.entity.Rack;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface HostMapper {

    @Select("SELECT * FROM xnet_aiops_hom_host WHERE cluster_id = #{clusterId} ORDER BY created_at DESC")
    List<Host> findByClusterId(@Param("clusterId") Long clusterId);

    @Select("SELECT * FROM xnet_aiops_hom_host ORDER BY created_at DESC")
    List<Host> findAll();

    @Select("SELECT * FROM xnet_aiops_hom_host WHERE id = #{id}")
    Host findById(@Param("id") Long id);

    @Select("SELECT * FROM xnet_aiops_hom_host WHERE uid = #{uid}")
    Host findByUid(@Param("uid") String uid);

    @Insert("INSERT INTO xnet_aiops_hom_host (uid, cluster_id, hostname, ip_address, ssh_port, ssh_user, " +
            "auth_type, encrypted_password, private_key, rack, node_label, status) " +
            "VALUES (#{uid}, #{clusterId}, #{hostname}, #{ipAddress}, #{sshPort}, #{sshUser}, " +
            "#{authType}, #{encryptedPassword}, #{privateKey}, #{rack}, #{nodeLabel}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Host host);

    @Update("UPDATE xnet_aiops_hom_host SET hostname=#{hostname}, ip_address=#{ipAddress}, " +
            "ssh_port=#{sshPort}, ssh_user=#{sshUser}, rack=#{rack}, node_label=#{nodeLabel} WHERE id=#{id}")
    int update(Host host);

    @Update("UPDATE xnet_aiops_hom_host SET status=#{status}, cpu_usage=#{cpuUsage}, used_mem_gb=#{usedMemGb}, " +
            "used_disk_gb=#{usedDiskGb}, last_heartbeat=NOW() WHERE id=#{id}")
    int updateMetrics(Host host);

    @Update("UPDATE xnet_aiops_hom_host SET os_type=#{osType}, os_version=#{osVersion}, cpu_arch=#{cpuArch}, " +
            "cpu_cores=#{cpuCores}, total_mem_gb=#{totalMemGb}, total_disk_gb=#{totalDiskGb} WHERE id=#{id}")
    int updateSystemInfo(Host host);

    @Update("UPDATE xnet_aiops_hom_host SET agent_status=#{agentStatus} WHERE id=#{id}")
    int updateAgentStatus(@Param("id") Long id, @Param("agentStatus") String agentStatus);

    @Delete("DELETE FROM xnet_aiops_hom_host WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM xnet_aiops_hom_host WHERE cluster_id = #{clusterId}")
    int countByClusterId(@Param("clusterId") Long clusterId);

    // --- Rack ---

    @Select("SELECT * FROM xnet_aiops_hom_rack WHERE cluster_id = #{clusterId}")
    List<Rack> findRacksByClusterId(@Param("clusterId") Long clusterId);

    @Insert("INSERT INTO xnet_aiops_hom_rack (cluster_id, rack_name, description) VALUES (#{clusterId}, #{rackName}, #{description})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRack(Rack rack);

    @Delete("DELETE FROM xnet_aiops_hom_rack WHERE id = #{id}")
    int deleteRack(@Param("id") Long id);
}
