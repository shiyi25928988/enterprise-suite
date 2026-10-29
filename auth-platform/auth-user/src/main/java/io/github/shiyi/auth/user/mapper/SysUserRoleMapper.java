package io.github.shiyi.auth.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.github.shiyi.auth.user.entity.SysUserRole;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * Role rows of several users in one round trip — the list/export paths used
     * to fire one query per row (21 queries for a 20-row page).
     */
    @Select("""
            <script>
            SELECT user_id, role_id FROM sys_user_role
            WHERE user_id IN
            <foreach collection="userIds" item="uid" open="(" separator="," close=")">#{uid}</foreach>
            </script>
            """)
    java.util.List<SysUserRole> selectByUserIds(@Param("userIds") java.util.Collection<Long> userIds);

    /** id + code + name of every role a user holds — detail view / userinfo claims. */
    @Select("""
            SELECT r.id, r.role_code, r.role_name
            FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND r.deleted = 0
            ORDER BY r.id
            """)
    java.util.List<java.util.Map<String, Object>> selectRoleDetailsByUserId(@Param("userId") Long userId);
}
