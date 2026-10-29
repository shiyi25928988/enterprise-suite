package io.github.shiyi.auth.user.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * sys_user row. See V2__platform_schema.sql.
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** DelegatingPasswordEncoder format, e.g. {bcrypt}... or {noop}... */
    private String password;

    private String realName;

    private String employeeNo;

    private String mobile;

    private String email;

    private Long orgId;

    private Long positionId;

    private String ldapDn;

    private String wecomUserid;

    /** Directory sync origin: "wecom" / "dingtalk" / null for local users */
    private String externalSource;

    /** User id inside the external directory (unique per source) */
    private String externalId;

    /** 1 local 2 LDAP 3 WeCom 4 DingTalk */
    private Integer source;

    /** 1 enabled 0 disabled 2 locked */
    private Integer status;

    /** 1 = TOTP second factor required at login */
    private Integer mfaEnabled;

    /** Base32 TOTP shared secret */
    private String totpSecret;

    private LocalDateTime passwordUpdateTime;

    private LocalDateTime lastLoginTime;

    private Integer failCount;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
