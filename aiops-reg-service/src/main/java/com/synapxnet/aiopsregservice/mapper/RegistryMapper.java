package com.synapxnet.aiopsregservice.mapper;

import com.synapxnet.aiopsregservice.entity.Registry;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RegistryMapper {

    @Select("SELECT * FROM xnet_aiops_reg_registry ORDER BY created_at DESC")
    List<Registry> findAll();

    @Select("SELECT * FROM xnet_aiops_reg_registry WHERE id = #{id}")
    Registry findById(Long id);

    @Select("SELECT * FROM xnet_aiops_reg_registry WHERE uid = #{uid}")
    Registry findByUid(String uid);

    @Insert("INSERT INTO xnet_aiops_reg_registry (uid, registry_name, registry_type, description, version, status, deploy_mode, " +
            "host_id, host, ssh_port, ssh_user, encrypted_password, encrypted_private_key, install_path, service_port, " +
            "cluster_id, namespace, release_name, helm_values, " +
            "endpoint, api_url, admin_user, encrypted_admin_password, use_ssl, cert_pem, " +
            "created_by, created_at, updated_at) " +
            "VALUES (#{uid}, #{registryName}, #{registryType}, #{description}, #{version}, #{status}, #{deployMode}, " +
            "#{hostId}, #{host}, #{sshPort}, #{sshUser}, #{encryptedPassword}, #{encryptedPrivateKey}, #{installPath}, #{servicePort}, " +
            "#{clusterId}, #{namespace}, #{releaseName}, #{helmValues}, " +
            "#{endpoint}, #{apiUrl}, #{adminUser}, #{encryptedAdminPassword}, #{useSsl}, #{certPem}, " +
            "#{createdBy}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Registry registry);

    @Update("UPDATE xnet_aiops_reg_registry SET registry_name=#{registryName}, description=#{description}, " +
            "version=#{version}, status=#{status}, " +
            "endpoint=#{endpoint}, api_url=#{apiUrl}, admin_user=#{adminUser}, " +
            "encrypted_admin_password=#{encryptedAdminPassword}, use_ssl=#{useSsl}, cert_pem=#{certPem}, " +
            "helm_values=#{helmValues}, updated_at=#{updatedAt} WHERE id=#{id}")
    void update(Registry registry);

    @Update("UPDATE xnet_aiops_reg_registry SET status=#{status}, updated_at=NOW() WHERE id=#{id}")
    void updateStatus(@Param("id") Long id, @Param("status") String status);

    @Delete("DELETE FROM xnet_aiops_reg_registry WHERE id = #{id}")
    void deleteById(Long id);
}
