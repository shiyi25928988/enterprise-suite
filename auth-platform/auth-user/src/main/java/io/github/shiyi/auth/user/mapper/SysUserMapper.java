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
}
