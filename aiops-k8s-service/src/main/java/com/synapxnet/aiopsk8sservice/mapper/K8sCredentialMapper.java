package com.synapxnet.aiopsk8sservice.mapper;

import com.synapxnet.aiopsk8sservice.entity.K8sCredential;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface K8sCredentialMapper {

    @Select("SELECT * FROM xnet_aiops_k8s_credential WHERE project_id = #{projectId} ORDER BY created_at DESC")
    List<K8sCredential> findByProjectId(@Param("projectId") Long projectId);

    @Select("SELECT * FROM xnet_aiops_k8s_credential WHERE id = #{id}")
    K8sCredential findById(@Param("id") Long id);

    @Insert("INSERT INTO xnet_aiops_k8s_credential (project_id, name, type, description, " +
            "username, password, private_key, passphrase, token, kubeconfig) " +
            "VALUES (#{projectId}, #{name}, #{type}, #{description}, " +
            "#{username}, #{password}, #{privateKey}, #{passphrase}, #{token}, #{kubeconfig})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(K8sCredential credential);

    @Update("UPDATE xnet_aiops_k8s_credential SET name=#{name}, type=#{type}, description=#{description}, " +
            "username=#{username}, password=#{password}, private_key=#{privateKey}, " +
            "passphrase=#{passphrase}, token=#{token}, kubeconfig=#{kubeconfig} WHERE id=#{id}")
    int update(K8sCredential credential);

    @Delete("DELETE FROM xnet_aiops_k8s_credential WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
