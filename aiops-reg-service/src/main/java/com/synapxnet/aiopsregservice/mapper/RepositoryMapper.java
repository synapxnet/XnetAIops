package com.synapxnet.aiopsregservice.mapper;

import com.synapxnet.aiopsregservice.entity.Repository;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RepositoryMapper {

    @Select("SELECT * FROM xnet_aiops_reg_repository WHERE registry_id=#{registryId} ORDER BY updated_at DESC")
    List<Repository> findByRegistryId(Long registryId);

    @Select("SELECT * FROM xnet_aiops_reg_repository WHERE registry_id=#{registryId} AND project_id=#{projectId} ORDER BY updated_at DESC")
    List<Repository> findByRegistryIdAndProjectId(@Param("registryId") Long registryId, @Param("projectId") Long projectId);

    @Select("SELECT * FROM xnet_aiops_reg_repository WHERE id=#{id}")
    Repository findById(Long id);

    @Insert("INSERT INTO xnet_aiops_reg_repository (registry_id, project_id, repo_name, tags_count, pull_count, latest_tag, updated_at) " +
            "VALUES (#{registryId}, #{projectId}, #{repoName}, #{tagsCount}, #{pullCount}, #{latestTag}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Repository repo);

    @Delete("DELETE FROM xnet_aiops_reg_repository WHERE registry_id=#{registryId}")
    void deleteByRegistryId(Long registryId);
}
