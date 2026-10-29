package io.github.shiyi.auth.user.web;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.shiyi.auth.common.api.ApiResult;
import io.github.shiyi.auth.common.api.PageResult;
import io.github.shiyi.auth.user.dto.LoginLogDTO;
import io.github.shiyi.auth.user.service.JdbcLoginLogService;
import io.github.shiyi.auth.user.service.LoginLogService;

/**
 * Login audit API (development doc: security audit). Read-only, ADMIN only.
 * Backed by sys_login_log (retention >= 180 days by policy).
 */
@RestController
@RequestMapping("/api/v1/login-logs")
public class LoginLogController {

    private final LoginLogService loginLogService;

    public LoginLogController(LoginLogService loginLogService) {
        this.loginLogService = loginLogService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<PageResult<LoginLogDTO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer success,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        // defensive cap mirrors JdbcLoginLogService.MAX_SIZE
        int safeSize = Math.min(Math.max(size, 1), JdbcLoginLogService.MAX_SIZE);
        return ApiResult.ok(loginLogService.page(Math.max(page, 1), safeSize,
                username, success, start, end));
    }
}
