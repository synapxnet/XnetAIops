package com.synapxnet.aiopsregservice.mapper;

import com.synapxnet.aiopsregservice.entity.Tag;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TagMapper {

    @Select("SELECT * FROM xnet_aiops_reg_tag WHERE repository_id=#{repositoryId} ORDER BY pushed_at DESC")
    List<Tag> findByRepositoryId(Long repositoryId);

    @Select("SELECT * FROM xnet_aiops_reg_tag WHERE id=#{id}")
    Tag findById(Long id);

    @Insert("INSERT INTO xnet_aiops_reg_tag (repository_id, tag_name, digest, size_bytes, architecture, os, pushed_at) " +
            "VALUES (#{repositoryId}, #{tagName}, #{digest}, #{sizeBytes}, #{architecture}, #{os}, #{pushedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Tag tag);

    @Delete("DELETE FROM xnet_aiops_reg_tag WHERE id=#{id}")
    void deleteById(Long id);

    @Delete("DELETE FROM xnet_aiops_reg_tag WHERE repository_id=#{repositoryId}")
    void deleteByRepositoryId(Long repositoryId);
}
