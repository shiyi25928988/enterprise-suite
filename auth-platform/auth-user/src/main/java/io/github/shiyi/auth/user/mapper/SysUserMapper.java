package io.github.shiyi.auth.user.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.github.shiyi.auth.user.entity.SysUser;

public interface SysUserMapper extends BaseMapper<SysUser> {

    @org.apache.ibatis.annotations.Select("""
            SELECT r.role_code
            FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND r.status = 1
            """)
    List<String> selectRoleCodes(Long userId);

    /** Role id of the built-in ADMIN role, null when the deployment renamed it away. */
    @org.apache.ibatis.annotations.Select("""
            SELECT id FROM sys_role
            WHERE role_code = 'ADMIN' AND status = 1 AND deleted = 0
            LIMIT 1
            """)
    Long selectAdminRoleId();

    /** Ids of every enabled account currently holding ADMIN (last-admin guard). */
    @org.apache.ibatis.annotations.Select("""
            SELECT u.id
            FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id = u.id
            JOIN sys_role r ON r.id = ur.role_id
            WHERE r.role_code = 'ADMIN' AND r.deleted = 0
              AND u.status = 1 AND u.deleted = 0
            """)
    java.util.List<Long> selectActiveAdminIds();
}
