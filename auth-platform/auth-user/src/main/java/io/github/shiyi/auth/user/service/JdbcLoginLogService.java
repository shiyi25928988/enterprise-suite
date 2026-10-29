package io.github.shiyi.auth.user.service;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import io.github.shiyi.auth.common.api.PageResult;
import io.github.shiyi.auth.user.dto.LoginLogDTO;
import io.github.shiyi.auth.user.entity.SysLoginLog;
import io.github.shiyi.auth.user.mapper.SysLoginLogMapper;

/**
 * JDBC-backed audit query (jdbc profile). Backed by sys_login_log; relies on
 * the MyBatis-Plus pagination interceptor (UserDomainConfig) for correct totals.
 */
public class JdbcLoginLogService implements LoginLogService {

    /** Hard cap so a client cannot ask for a million rows at once. */
    public static final int MAX_SIZE = 200;

    private final SysLoginLogMapper mapper;

    public JdbcLoginLogService(SysLoginLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public PageResult<LoginLogDTO> page(int page, int size, String username,
                                        Integer success, LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<SysLoginLog>()
                .eq(username != null && !username.isBlank(), SysLoginLog::getUsername, username)
                .eq(success != null, SysLoginLog::getSuccess, success)
                .ge(start != null, SysLoginLog::getCreateTime, start)
                .le(end != null, SysLoginLog::getCreateTime, end)
                .orderByDesc(SysLoginLog::getCreateTime)
                .orderByDesc(SysLoginLog::getId);

        Page<SysLoginLog> result = mapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_SIZE)), wrapper);

        var records = result.getRecords().stream()
                .map(l -> new LoginLogDTO(l.getId(), l.getUsername(), l.getUserId(),
                        l.getLoginType(), l.getSuccess(), l.getFailReason(),
                        l.getIp(), l.getUserAgent(), l.getCreateTime()))
                .toList();
        return new PageResult<>(records, result.getTotal(), page, size);
    }
}
