package io.github.shiyi.auth.user.service;

import java.time.LocalDateTime;

import io.github.shiyi.auth.common.api.PageResult;
import io.github.shiyi.auth.user.dto.LoginLogDTO;

/**
 * Read-only access to the login audit trail (sys_login_log).
 * Admin-facing; the write side lives in the login flow
 * (UserAccountService.recordLogin), not here.
 */
public interface LoginLogService {

    /**
     * Paged audit query with optional filters.
     *
     * @param page     1-based page number
     * @param size     page size (capped by the caller/controller)
     * @param username exact-match filter, nullable
     * @param success  1 = successful logins, 0 = failures, null = both
     * @param start    inclusive lower bound on createTime, nullable
     * @param end      inclusive upper bound on createTime, nullable
     */
    PageResult<LoginLogDTO> page(int page, int size, String username,
                                 Integer success, LocalDateTime start, LocalDateTime end);

    /**
     * Retention enforcement: delete audit rows strictly older than the given
     * instant. Returns the number of rows removed (0 in demo mode).
     */
    int purgeBefore(LocalDateTime threshold);
}
