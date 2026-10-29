package io.github.shiyi.auth.user.dto;

import java.time.LocalDateTime;

/**
 * One row of the login audit trail (sys_login_log), as returned by the
 * admin audit API. Fields mirror the entity; nothing secret here —
 * failReason is an internal reason code, not the submitted credential.
 */
public record LoginLogDTO(
        Long id,
        String username,
        Long userId,
        String loginType,
        Integer success,
        String failReason,
        String ip,
        String userAgent,
        LocalDateTime createTime) {
}
