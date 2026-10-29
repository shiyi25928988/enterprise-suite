package io.github.shiyi.auth.user.service;

import java.time.LocalDateTime;

import io.github.shiyi.auth.common.api.PageResult;
import io.github.shiyi.auth.user.entity.SysOperationLog;

/**
 * Management operation audit (dual mode: JDBC / in-memory ring buffer).
 */
public interface OperationLogService {

    /** Page size cap shared with the controller (mirrors JdbcLoginLogService). */
    int MAX_SIZE = 200;

    void record(SysOperationLog entry);

    PageResult<SysOperationLog> page(int page, int size, String operator,
                                     String action, LocalDateTime start, LocalDateTime end);
}
