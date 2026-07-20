package com.synapxnet.aiopsk8sservice.mapper;

import com.synapxnet.aiopsk8sservice.entity.K8sCluster;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface K8sClusterMapper {

    @Select("SELECT * FROM xnet_aiops_k8s_cluster ORDER BY created_at DESC")
    List<K8sCluster> findAll();

    @Select("SELECT * FROM xnet_aiops_k8s_cluster WHERE id = #{id}")
    K8sCluster findById(@Param("id") Long id);

    @Select("SELECT * FROM xnet_aiops_k8s_cluster WHERE uid = #{uid}")
    K8sCluster findByUid(@Param("uid") String uid);

    @Insert("INSERT INTO xnet_aiops_k8s_cluster (uid, name, description, api_server_url, kubeconfig_content, version, node_count, namespace_count, status, provider, network_plugin, container_runtime, ssh_host, ssh_port, ssh_user, ssh_password, ssh_key) " +
            "VALUES (#{uid}, #{name}, #{description}, #{apiServerUrl}, #{kubeconfigContent}, #{version}, #{nodeCount}, #{namespaceCount}, #{status}, #{provider}, #{networkPlugin}, #{containerRuntime}, #{sshHost}, #{sshPort}, #{sshUser}, #{sshPassword}, #{sshKey})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(K8sCluster cluster);

    @Update("UPDATE xnet_aiops_k8s_cluster SET name=#{name}, description=#{description}, api_server_url=#{apiServerUrl}, " +
            "kubeconfig_content=#{kubeconfigContent}, version=#{version}, node_count=#{nodeCount}, namespace_count=#{namespaceCount}, " +
            "status=#{status}, provider=#{provider}, network_plugin=#{networkPlugin}, container_runtime=#{containerRuntime}, " +
            "ssh_host=#{sshHost}, ssh_port=#{sshPort}, ssh_user=#{sshUser}, ssh_password=#{sshPassword}, ssh_key=#{sshKey} WHERE id=#{id}")
    int update(K8sCluster cluster);

    @Update("UPDATE xnet_aiops_k8s_cluster SET ssh_host=#{sshHost}, ssh_port=#{sshPort}, ssh_user=#{sshUser}, ssh_password=#{sshPassword}, ssh_key=#{sshKey} WHERE id=#{id}")
    int updateSsh(K8sCluster cluster);

    @Update("UPDATE xnet_aiops_k8s_cluster SET status=#{status} WHERE id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Update("UPDATE xnet_aiops_k8s_cluster SET node_count=#{nodeCount}, namespace_count=#{namespaceCount}, version=#{version} WHERE id=#{id}")
    int updateClusterInfo(@Param("id") Long id, @Param("nodeCount") Integer nodeCount, @Param("namespaceCount") Integer namespaceCount, @Param("version") String version);

    @Delete("DELETE FROM xnet_aiops_k8s_cluster WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
