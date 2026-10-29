package io.github.shiyi.auth.user.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.shiyi.auth.common.api.PageResult;
import io.github.shiyi.auth.user.dto.LoginLogDTO;

/**
 * Demo-mode stub: in-memory mode keeps no durable audit trail, so queries
 * return an empty page. Activate the "jdbc" profile for real audit data.
 */
public class InMemoryLoginLogService implements LoginLogService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryLoginLogService.class);

    @Override
    public PageResult<LoginLogDTO> page(int page, int size, String username,
                                        Integer success, LocalDateTime start, LocalDateTime end) {
        log.debug("Login audit query in demo mode - returning empty page (in-memory mode keeps no audit trail)");
        return new PageResult<>(java.util.List.of(), 0, page, size);
    }

    @Override
    public int purgeBefore(LocalDateTime threshold) {
        return 0; // nothing durable to purge in demo mode
    }
}
