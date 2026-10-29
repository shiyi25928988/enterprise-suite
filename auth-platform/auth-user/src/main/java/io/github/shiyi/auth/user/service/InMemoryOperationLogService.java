package io.github.shiyi.auth.user.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import io.github.shiyi.auth.common.api.PageResult;
import io.github.shiyi.auth.user.entity.SysOperationLog;

/**
 * In-memory fallback: keeps the most recent {@value #CAPACITY} entries so the
 * audit page also works in demo mode (data resets on restart by design).
 */
public class InMemoryOperationLogService implements OperationLogService {

    private static final int CAPACITY = 1000;

    private final ConcurrentLinkedDeque<SysOperationLog> store = new ConcurrentLinkedDeque<>();
    private final AtomicLong idSeq = new AtomicLong(0);

    @Override
    public void record(SysOperationLog entry) {
        entry.setId(idSeq.incrementAndGet());
        if (entry.getCreateTime() == null) {
            entry.setCreateTime(LocalDateTime.now());
        }
        store.addFirst(entry);
        while (store.size() > CAPACITY) {
            store.pollLast();
        }
    }

    @Override
    public PageResult<SysOperationLog> page(int page, int size, String operator,
                                            String action, LocalDateTime start, LocalDateTime end) {
        List<SysOperationLog> filtered = store.stream()
                .filter(e -> operator == null || operator.isBlank() || operator.equals(e.getOperator()))
                .filter(e -> action == null || action.isBlank() || action.equals(e.getAction()))
                .filter(e -> start == null || !e.getCreateTime().isBefore(start))
                .filter(e -> end == null || !e.getCreateTime().isAfter(end))
                .sorted(Comparator.comparingLong(SysOperationLog::getId).reversed())
                .toList();
        int from = Math.min((page - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        return new PageResult<>(filtered.subList(from, to), filtered.size(), page, size);
    }
}
