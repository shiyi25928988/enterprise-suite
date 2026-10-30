package io.github.shiyi.auth.user.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * M11: daily retention sweep for the message center, same shape as
 * {@link LoginLogRetention}. Read messages disappear after
 * {@code auth.message.read-retention-days} (default 30); unread ones get the
 * longer {@code auth.message.unread-retention-days} (default 180) so a user
 * on holiday does not come back to an emptied inbox.
 */
public class MessageRetention {

    private static final Logger log = LoggerFactory.getLogger(MessageRetention.class);

    private final MessageService messages;
    private final int readDays;
    private final int unreadDays;

    public MessageRetention(MessageService messages, int readDays, int unreadDays) {
        this.messages = messages;
        this.readDays = Math.max(readDays, 1);
        this.unreadDays = Math.max(unreadDays, this.readDays);
    }

    @Scheduled(cron = "${auth.message.cleanup-cron:0 10 3 * * *}")
    public void purgeExpired() {
        int removed = messages.purge(
                LocalDateTime.now().minusDays(readDays),
                LocalDateTime.now().minusDays(unreadDays));
        if (removed > 0) {
            log.info("Message retention: removed {} rows (read >{}d, unread >{}d)",
                    removed, readDays, unreadDays);
        } else {
            log.debug("Message retention: nothing to purge");
        }
    }
}
