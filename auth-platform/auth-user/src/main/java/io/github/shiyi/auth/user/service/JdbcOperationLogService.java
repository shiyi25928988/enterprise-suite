package io.github.shiyi.auth.user.service;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import io.github.shiyi.auth.common.api.PageResult;
import io.github.shiyi.auth.user.entity.SysOperationLog;
import io.github.shiyi.auth.user.mapper.SysOperationLogMapper;

public class JdbcOperationLogService implements OperationLogService {

    private final SysOperationLogMapper mapper;

    public JdbcOperationLogService(SysOperationLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void record(SysOperationLog entry) {
        if (entry.getCreateTime() == null) {
            entry.setCreateTime(LocalDateTime.now());
        }
        mapper.insert(entry);
    }

    @Override
    public PageResult<SysOperationLog> page(int page, int size, String operator,
                                            String action, LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<SysOperationLog>()
                .eq(operator != null && !operator.isBlank(), SysOperationLog::getOperator, operator)
                .eq(action != null && !action.isBlank(), SysOperationLog::getAction, action)
                .ge(start != null, SysOperationLog::getCreateTime, start)
                .le(end != null, SysOperationLog::getCreateTime, end)
                .orderByDesc(SysOperationLog::getId);
        Page<SysOperationLog> result = mapper.selectPage(new Page<>(page, size), wrapper);
        List<SysOperationLog> rows = result.getRecords();
        return new PageResult<>(rows, result.getTotal(), page, size);
    }
}
