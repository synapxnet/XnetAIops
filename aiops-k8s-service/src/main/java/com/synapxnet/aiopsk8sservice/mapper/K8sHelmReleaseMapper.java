package com.synapxnet.aiopsk8sservice.mapper;

import com.synapxnet.aiopsk8sservice.entity.K8sHelmRelease;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface K8sHelmReleaseMapper {

    @Select("SELECT * FROM xnet_aiops_k8s_helm_release WHERE cluster_id = #{clusterId} ORDER BY created_at DESC")
    List<K8sHelmRelease> findByClusterId(@Param("clusterId") Long clusterId);

    @Select("SELECT * FROM xnet_aiops_k8s_helm_release WHERE cluster_id = #{clusterId} AND namespace = #{namespace} AND release_name = #{releaseName}")
    K8sHelmRelease findByClusterAndRelease(@Param("clusterId") Long clusterId, @Param("namespace") String namespace, @Param("releaseName") String releaseName);

    @Select("SELECT * FROM xnet_aiops_k8s_helm_release WHERE id = #{id}")
    K8sHelmRelease findById(@Param("id") Long id);

    @Insert("INSERT INTO xnet_aiops_k8s_helm_release (cluster_id, namespace, release_name, chart_name, chart_version, app_version, values_override, status, revision) " +
            "VALUES (#{clusterId}, #{namespace}, #{releaseName}, #{chartName}, #{chartVersion}, #{appVersion}, #{valuesOverride}, #{status}, #{revision})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(K8sHelmRelease release);

    @Update("UPDATE xnet_aiops_k8s_helm_release SET status=#{status}, chart_version=#{chartVersion}, app_version=#{appVersion}, " +
            "values_override=#{valuesOverride}, revision=#{revision}, notes=#{notes} WHERE id=#{id}")
    int update(K8sHelmRelease release);

    @Delete("DELETE FROM xnet_aiops_k8s_helm_release WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
