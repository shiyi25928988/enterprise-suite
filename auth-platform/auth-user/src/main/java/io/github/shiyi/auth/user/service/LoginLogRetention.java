package io.github.shiyi.auth.user.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Daily retention enforcement for the login audit trail. Deletes rows older
 * than {@code auth.audit.retention-days} (default 90) at
 * {@code auth.audit.cleanup-cron} (default 03:00 server time).
 *
 * <p>Runs against whatever {@link LoginLogService} bean is wired: the JDBC
 * implementation issues the DELETE, the in-memory one is a counted no-op —
 * so the scheduler is safe to register in every mode.</p>
 */
public class LoginLogRetention {

    private static final Logger log = LoggerFactory.getLogger(LoginLogRetention.class);

    private final LoginLogService loginLogs;
    private final int retentionDays;

    public LoginLogRetention(LoginLogService loginLogs, int retentionDays) {
        this.loginLogs = loginLogs;
        this.retentionDays = Math.max(retentionDays, 1);
    }

    @Scheduled(cron = "${auth.audit.cleanup-cron:0 0 3 * * *}")
    public void purgeExpired() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        int removed = loginLogs.purgeBefore(threshold);
        if (removed > 0) {
            log.info("Login audit retention: removed {} rows older than {} days ({})",
                    removed, retentionDays, threshold);
        } else {
            log.debug("Login audit retention: nothing older than {} to purge", threshold);
        }
    }
}
