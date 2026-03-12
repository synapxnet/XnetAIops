package com.synapxnet.aiopsclmservice.mapper;

import com.synapxnet.aiopsclmservice.entity.HadoopVersion;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Hadoop 版本 Mapper
 */
@Mapper
public interface HadoopVersionMapper {

    @Select("SELECT id, version, version_type as versionType, release_date as releaseDate, " +
            "download_url as downloadUrl, is_latest as isLatest, " +
            "created_at as createdAt, updated_at as updatedAt " +
            "FROM xnet_aiops_clm_hadoop_version ORDER BY version DESC")
    List<HadoopVersion> findAll();

    @Select("SELECT id, version, version_type as versionType, release_date as releaseDate, " +
            "download_url as downloadUrl, is_latest as isLatest, " +
            "created_at as createdAt, updated_at as updatedAt " +
            "FROM xnet_aiops_clm_hadoop_version WHERE version_type = #{versionType} " +
            "ORDER BY version DESC")
    List<HadoopVersion> findByVersionType(@Param("versionType") String versionType);

    @Select("SELECT id, version, version_type as versionType, release_date as releaseDate, " +
            "download_url as downloadUrl, is_latest as isLatest, " +
            "created_at as createdAt, updated_at as updatedAt " +
            "FROM xnet_aiops_clm_hadoop_version WHERE version_type = 'stable' " +
            "ORDER BY version DESC")
    List<HadoopVersion> findStableVersions();

    @Select("SELECT id, version, version_type as versionType, release_date as releaseDate, " +
            "download_url as downloadUrl, is_latest as isLatest, " +
            "created_at as createdAt, updated_at as updatedAt " +
            "FROM xnet_aiops_clm_hadoop_version WHERE version = #{version}")
    HadoopVersion findByVersion(@Param("version") String version);

    @Insert("INSERT INTO xnet_aiops_clm_hadoop_version " +
            "(version, version_type, release_date, download_url, is_latest) " +
            "VALUES (#{version}, #{versionType}, #{releaseDate}, #{downloadUrl}, #{isLatest})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(HadoopVersion hadoopVersion);

    @Update("UPDATE xnet_aiops_clm_hadoop_version SET " +
            "release_date = #{releaseDate}, download_url = #{downloadUrl}, " +
            "is_latest = #{isLatest} WHERE version = #{version}")
    int update(HadoopVersion hadoopVersion);

    @Update("UPDATE xnet_aiops_clm_hadoop_version SET is_latest = 0 WHERE version_type = #{versionType}")
    int resetLatestFlag(@Param("versionType") String versionType);

    @Select("SELECT COUNT(*) FROM xnet_aiops_clm_hadoop_version WHERE version_type = #{versionType}")
    int countByVersionType(@Param("versionType") String versionType);
}
