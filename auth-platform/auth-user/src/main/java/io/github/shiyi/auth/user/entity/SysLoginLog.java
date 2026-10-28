package io.github.shiyi.auth.user.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * sys_login_log row: one entry per login attempt (success or failure).
 * Retention: >= 180 days (compliance), enforced by scheduled cleanup later.
 */
@Data
@TableName("sys_login_log")
public class SysLoginLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private Long userId;

    /** PASSWORD / SMS / WECOM / QRCODE */
    private String loginType;

    private Integer success;

    private String failReason;

    private String clientId;

    private String ip;

    private String userAgent;

    private String location;

    private LocalDateTime createTime;
}
