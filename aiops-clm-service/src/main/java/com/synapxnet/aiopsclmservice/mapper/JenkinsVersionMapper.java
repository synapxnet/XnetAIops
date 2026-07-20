package com.synapxnet.aiopsclmservice.mapper;

import com.synapxnet.aiopsclmservice.entity.JenkinsVersion;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Jenkins 版本 Mapper
 */
@Mapper
public interface JenkinsVersionMapper {

    @Select("SELECT id, version, version_type as versionType, release_date as releaseDate, " +
            "download_url as downloadUrl, sha256, is_lts as isLts, is_latest as isLatest, " +
            "created_at as createdAt, updated_at as updatedAt " +
            "FROM xnet_aiops_clm_jenkins_version ORDER BY release_date DESC, version DESC")
    List<JenkinsVersion> findAll();

    @Select("SELECT id, version, version_type as versionType, release_date as releaseDate, " +
            "download_url as downloadUrl, sha256, is_lts as isLts, is_latest as isLatest, " +
            "created_at as createdAt, updated_at as updatedAt " +
            "FROM xnet_aiops_clm_jenkins_version WHERE version_type = #{versionType} " +
            "ORDER BY release_date DESC, version DESC")
    List<JenkinsVersion> findByVersionType(@Param("versionType") String versionType);

    @Select("SELECT id, version, version_type as versionType, release_date as releaseDate, " +
            "download_url as downloadUrl, sha256, is_lts as isLts, is_latest as isLatest, " +
            "created_at as createdAt, updated_at as updatedAt " +
            "FROM xnet_aiops_clm_jenkins_version WHERE is_lts = 1 " +
            "ORDER BY release_date DESC, version DESC")
    List<JenkinsVersion> findLtsVersions();

    @Select("SELECT id, version, version_type as versionType, release_date as releaseDate, " +
            "download_url as downloadUrl, sha256, is_lts as isLts, is_latest as isLatest, " +
            "created_at as createdAt, updated_at as updatedAt " +
            "FROM xnet_aiops_clm_jenkins_version WHERE version = #{version} AND version_type = #{versionType}")
    JenkinsVersion findByVersionAndType(@Param("version") String version, @Param("versionType") String versionType);

    @Insert("INSERT INTO xnet_aiops_clm_jenkins_version " +
            "(version, version_type, release_date, download_url, sha256, is_lts, is_latest) " +
            "VALUES (#{version}, #{versionType}, #{releaseDate}, #{downloadUrl}, #{sha256}, #{isLts}, #{isLatest})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(JenkinsVersion jenkinsVersion);

    @Update("UPDATE xnet_aiops_clm_jenkins_version SET " +
            "release_date = #{releaseDate}, download_url = #{downloadUrl}, sha256 = #{sha256}, " +
            "is_lts = #{isLts}, is_latest = #{isLatest} " +
            "WHERE version = #{version} AND version_type = #{versionType}")
    int update(JenkinsVersion jenkinsVersion);

    @Update("UPDATE xnet_aiops_clm_jenkins_version SET is_latest = 0 WHERE version_type = #{versionType}")
    int resetLatestFlag(@Param("versionType") String versionType);

    @Delete("DELETE FROM xnet_aiops_clm_jenkins_version WHERE version_type = #{versionType}")
    int deleteByVersionType(@Param("versionType") String versionType);

    @Select("SELECT COUNT(*) FROM xnet_aiops_clm_jenkins_version WHERE version_type = #{versionType}")
    int countByVersionType(@Param("versionType") String versionType);
}
