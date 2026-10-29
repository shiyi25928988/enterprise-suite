package io.github.shiyi.auth.user.web;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.shiyi.auth.common.api.ApiResult;
import io.github.shiyi.auth.common.api.ErrorCode;
import io.github.shiyi.auth.common.exception.BizException;
import io.github.shiyi.auth.user.sync.DirectoryProvider;
import io.github.shiyi.auth.user.sync.SyncResult;
import io.github.shiyi.auth.user.sync.UserSyncService;

/**
 * Directory sync API (M8). ADMIN only:
 * <pre>
 *   GET  /api/v1/sync            → enabled sources + last-run result per source
 *   POST /api/v1/sync/{source}   → trigger a full sync now (wecom / dingtalk)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final ObjectProvider<UserSyncService> syncService;
    private final ObjectProvider<DirectoryProvider> providers;

    public SyncController(ObjectProvider<UserSyncService> syncService,
                          ObjectProvider<DirectoryProvider> providers) {
        this.syncService = syncService;
        this.providers = providers;
    }

    public record SourceStatus(String source, boolean enabled, SyncResult lastRun) {
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<List<SourceStatus>> status() {
        UserSyncService sync = syncService.getIfAvailable();
        List<SourceStatus> rows = providers.orderedStream()
                .map(p -> new SourceStatus(p.source(), sync != null,
                        sync == null ? null : sync.lastRun(p.source())))
                .toList();
        return ApiResult.ok(rows);
    }

    @PostMapping("/{source}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<SyncResult> trigger(@PathVariable String source) {
        UserSyncService sync = syncService.getIfAvailable();
        if (sync == null) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "directory sync requires the jdbc profile (persistent user store)");
        }
        if (sync.sources().stream().noneMatch(source::equals)) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "no directory provider enabled for source: " + source
                            + " (enable via auth.sync.*.enabled=true)");
        }
        return ApiResult.ok(sync.sync(source));
    }
}
