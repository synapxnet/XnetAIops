package com.synapxnet.aiopsusrservice.mapper;

import com.synapxnet.aiopsusrservice.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM xnet_aiops_usr_user ORDER BY created_at DESC")
    List<User> findAll();

    @Select("SELECT * FROM xnet_aiops_usr_user WHERE id = #{id}")
    User findById(@Param("id") Long id);

    @Select("SELECT * FROM xnet_aiops_usr_user WHERE username = #{username}")
    User findByUsername(@Param("username") String username);

    @Select("SELECT * FROM xnet_aiops_usr_user WHERE phone = #{phone}")
    User findByPhone(@Param("phone") String phone);

    @Insert("INSERT INTO xnet_aiops_usr_user (uid, username, password, email, phone, user_type, status) " +
            "VALUES (#{uid}, #{username}, #{password}, #{email}, #{phone}, #{userType}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("UPDATE xnet_aiops_usr_user SET email=#{email}, phone=#{phone}, user_type=#{userType}, status=#{status} WHERE id=#{id}")
    int update(User user);

    @Update("UPDATE xnet_aiops_usr_user SET password=#{password} WHERE id=#{id}")
    int updatePassword(@Param("id") Long id, @Param("password") String password);

    @Update("UPDATE xnet_aiops_usr_user SET last_login_at=NOW() WHERE id=#{id}")
    int updateLastLogin(@Param("id") Long id);

    @Delete("DELETE FROM xnet_aiops_usr_user WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    // --- Session ---

    @Insert("INSERT INTO xnet_aiops_usr_session (user_id, token, ip, expire_at) VALUES (#{userId}, #{token}, #{ip}, #{expireAt})")
    int insertSession(@Param("userId") Long userId, @Param("token") String token,
                      @Param("ip") String ip, @Param("expireAt") java.time.LocalDateTime expireAt);

    @Select("SELECT u.* FROM xnet_aiops_usr_user u " +
            "JOIN xnet_aiops_usr_session s ON u.id = s.user_id " +
            "WHERE s.token = #{token} AND s.expire_at > NOW()")
    User findByToken(@Param("token") String token);

    @Delete("DELETE FROM xnet_aiops_usr_session WHERE token = #{token}")
    int deleteSession(@Param("token") String token);

    @Delete("DELETE FROM xnet_aiops_usr_session WHERE user_id = #{userId}")
    int deleteSessionsByUserId(@Param("userId") Long userId);
}
