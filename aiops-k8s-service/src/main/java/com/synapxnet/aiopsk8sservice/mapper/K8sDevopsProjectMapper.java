package com.synapxnet.aiopsk8sservice.mapper;

import com.synapxnet.aiopsk8sservice.entity.K8sDevopsProject;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface K8sDevopsProjectMapper {

    @Select("SELECT p.*, (SELECT COUNT(*) FROM xnet_aiops_k8s_pipeline WHERE project_id = p.id) AS pipeline_count " +
            "FROM xnet_aiops_k8s_devops_project p ORDER BY p.created_at DESC")
    List<K8sDevopsProject> findAll();

    @Select("SELECT * FROM xnet_aiops_k8s_devops_project WHERE id = #{id}")
    K8sDevopsProject findById(@Param("id") Long id);

    @Insert("INSERT INTO xnet_aiops_k8s_devops_project (name, description, jenkins_url, jenkins_user, jenkins_token, cluster_id, status) " +
            "VALUES (#{name}, #{description}, #{jenkinsUrl}, #{jenkinsUser}, #{jenkinsToken}, #{clusterId}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(K8sDevopsProject project);

    @Update("UPDATE xnet_aiops_k8s_devops_project SET name=#{name}, description=#{description}, " +
            "jenkins_url=#{jenkinsUrl}, jenkins_user=#{jenkinsUser}, jenkins_token=#{jenkinsToken}, " +
            "cluster_id=#{clusterId}, status=#{status} WHERE id=#{id}")
    int update(K8sDevopsProject project);

    @Delete("DELETE FROM xnet_aiops_k8s_devops_project WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
