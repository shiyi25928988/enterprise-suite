package io.github.shiyi.auth.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * sys_user_role join row.
 */
@Data
@TableName("sys_user_role")
public class SysUserRole {

    private Long userId;

    private Long roleId;
}
