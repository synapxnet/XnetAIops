package com.synapxnet.aiopsk8sservice.mapper;

import com.synapxnet.aiopsk8sservice.entity.K8sHelmRepo;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface K8sHelmRepoMapper {

    @Select("SELECT * FROM xnet_aiops_k8s_helm_repo ORDER BY created_at DESC")
    List<K8sHelmRepo> findAll();

    @Select("SELECT * FROM xnet_aiops_k8s_helm_repo WHERE id = #{id}")
    K8sHelmRepo findById(@Param("id") Long id);

    @Select("SELECT * FROM xnet_aiops_k8s_helm_repo WHERE name = #{name}")
    K8sHelmRepo findByName(@Param("name") String name);

    @Insert("INSERT INTO xnet_aiops_k8s_helm_repo (name, url, description, auth_type, username, password, status) " +
            "VALUES (#{name}, #{url}, #{description}, #{authType}, #{username}, #{password}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(K8sHelmRepo repo);

    @Update("UPDATE xnet_aiops_k8s_helm_repo SET name=#{name}, url=#{url}, description=#{description}, auth_type=#{authType}, " +
            "username=#{username}, password=#{password}, status=#{status}, last_synced_at=#{lastSyncedAt} WHERE id=#{id}")
    int update(K8sHelmRepo repo);

    @Delete("DELETE FROM xnet_aiops_k8s_helm_repo WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
