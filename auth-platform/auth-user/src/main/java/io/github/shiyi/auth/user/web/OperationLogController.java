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
import io.github.shiyi.auth.user.entity.SysOperationLog;
import io.github.shiyi.auth.user.service.OperationLogService;

/**
 * Management operation audit query API. Read-only, ADMIN only.
 * Backed by sys_operation_log (V8) in jdbc mode, ring buffer in demo mode.
 */
@RestController
@RequestMapping("/api/v1/operation-logs")
public class OperationLogController {

    private final OperationLogService operationLogs;

    public OperationLogController(OperationLogService operationLogs) {
        this.operationLogs = operationLogs;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<PageResult<SysOperationLog>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String action,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        int safeSize = Math.min(Math.max(size, 1), OperationLogService.MAX_SIZE);
        return ApiResult.ok(operationLogs.page(Math.max(page, 1), safeSize,
                operator, action, start, end));
    }
}
