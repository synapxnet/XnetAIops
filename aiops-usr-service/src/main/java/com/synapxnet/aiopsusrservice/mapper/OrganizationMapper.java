package com.synapxnet.aiopsusrservice.mapper;

import com.synapxnet.aiopsusrservice.entity.OrganizationMembership;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrganizationMapper {

    /**
     * 按登录手机号查询有效且明确授权的组织成员关系。
     *
     * @param phone 登录手机号
     * @return 当前用户可见的成员关系
     */
    @Select("SELECT t.uid AS tenantUid, t.tenant_name AS tenantName, " +
            "d.uid AS deptUid, d.dept_name AS deptName, " +
            "tm.uid AS teamUid, tm.team_name AS teamName " +
            "FROM xnet_aiops_usr_user u " +
            "JOIN xnet_aiops_usr_organization_membership m ON m.user_id = u.id AND m.status = 1 " +
            "JOIN xnet_aiops_sys_tenant t ON t.uid = m.tenant_uid AND t.status = 1 " +
            "LEFT JOIN xnet_aiops_sys_department d ON d.uid = m.dept_uid AND d.status = 1 " +
            "LEFT JOIN xnet_aiops_sys_team tm ON tm.uid = m.team_uid AND tm.status = 1 " +
            "WHERE u.phone = #{phone} AND u.status = 'active' " +
            "ORDER BY t.tenant_name, d.dept_name, tm.team_name")
    List<OrganizationMembership> findActiveMembershipsByPhone(@Param("phone") String phone);
}
