package io.github.shiyi.auth.user.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * sys_operation_log row — one management API write call. See V8__operation_log.sql.
 */
@Data
@TableName("sys_operation_log")
public class SysOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String operator;

    private String method;

    private String uri;

    /** Semantic action, e.g. USER_STATUS / ROLE_DELETE / APP_SECRET_ROTATE */
    private String action;

    private Integer statusCode;

    private String ip;

    private LocalDateTime createTime;
}
