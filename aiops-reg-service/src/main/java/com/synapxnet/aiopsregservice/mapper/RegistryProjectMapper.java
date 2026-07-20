package com.synapxnet.aiopsregservice.mapper;

import com.synapxnet.aiopsregservice.entity.RegistryProject;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RegistryProjectMapper {

    @Select("SELECT * FROM xnet_aiops_reg_project WHERE registry_id=#{registryId} ORDER BY created_at DESC")
    List<RegistryProject> findByRegistryId(Long registryId);

    @Select("SELECT * FROM xnet_aiops_reg_project WHERE id=#{id}")
    RegistryProject findById(Long id);

    @Insert("INSERT INTO xnet_aiops_reg_project (registry_id, project_name, visibility, repo_count, created_at) " +
            "VALUES (#{registryId}, #{projectName}, #{visibility}, #{repoCount}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(RegistryProject project);

    @Delete("DELETE FROM xnet_aiops_reg_project WHERE id=#{id}")
    void deleteById(Long id);

    @Delete("DELETE FROM xnet_aiops_reg_project WHERE registry_id=#{registryId}")
    void deleteByRegistryId(Long registryId);
}
