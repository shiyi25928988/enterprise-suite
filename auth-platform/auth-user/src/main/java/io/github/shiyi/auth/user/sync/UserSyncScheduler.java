package io.github.shiyi.auth.user.sync;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Cron-triggered full sync across every enabled source. Registered only in
 * jdbc mode; {@code auth.sync.cron} defaults to "-" (Spring's disabled cron),
 * so nothing runs until an operator opts in.
 */
public class UserSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(UserSyncScheduler.class);

    private final ObjectProvider<UserSyncService> service;

    public UserSyncScheduler(ObjectProvider<UserSyncService> service) {
        this.service = service;
    }

    @Scheduled(cron = "${auth.sync.cron:-}")
    public void run() {
        UserSyncService sync = service.getIfAvailable();
        if (sync == null || sync.sources().isEmpty()) {
            return;
        }
        for (String source : sync.sources()) {
            try {
                sync.sync(source);
            } catch (Exception e) {
                // one broken source must not block the others' schedule
                log.warn("scheduled sync of {} failed: {}", source, e.getMessage());
            }
        }
    }
}
