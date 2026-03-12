package com.synapxnet.aiopsk8sservice.mapper;

import com.synapxnet.aiopsk8sservice.entity.K8sAppTemplate;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface K8sAppTemplateMapper {

    @Select("SELECT * FROM xnet_aiops_k8s_app_template ORDER BY sort_order ASC")
    List<K8sAppTemplate> findAll();

    @Select("SELECT * FROM xnet_aiops_k8s_app_template WHERE category = #{category} ORDER BY sort_order ASC")
    List<K8sAppTemplate> findByCategory(@Param("category") String category);

    @Select("SELECT DISTINCT category FROM xnet_aiops_k8s_app_template ORDER BY category")
    List<String> findAllCategories();

    @Select("SELECT * FROM xnet_aiops_k8s_app_template WHERE is_featured = 1 ORDER BY sort_order ASC")
    List<K8sAppTemplate> findFeatured();

    @Select("SELECT * FROM xnet_aiops_k8s_app_template WHERE id = #{id}")
    K8sAppTemplate findById(@Param("id") Long id);

    @Insert("INSERT INTO xnet_aiops_k8s_app_template (name, display_name, description, category, icon, helm_repo_name, helm_repo_url, chart_name, chart_version, default_values, doc_url, is_featured, sort_order) " +
            "VALUES (#{name}, #{displayName}, #{description}, #{category}, #{icon}, #{helmRepoName}, #{helmRepoUrl}, #{chartName}, #{chartVersion}, #{defaultValues}, #{docUrl}, #{isFeatured}, #{sortOrder})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(K8sAppTemplate template);

    @Update("UPDATE xnet_aiops_k8s_app_template SET name=#{name}, display_name=#{displayName}, description=#{description}, " +
            "category=#{category}, icon=#{icon}, helm_repo_name=#{helmRepoName}, helm_repo_url=#{helmRepoUrl}, " +
            "chart_name=#{chartName}, chart_version=#{chartVersion}, default_values=#{defaultValues}, " +
            "doc_url=#{docUrl}, is_featured=#{isFeatured}, sort_order=#{sortOrder} WHERE id=#{id}")
    int update(K8sAppTemplate template);

    @Delete("DELETE FROM xnet_aiops_k8s_app_template WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
