package io.github.shiyi.auth.user.notify;

import java.util.Map;

/**
 * One delivery mechanism for platform events (M10 introduced webhooks, M10b
 * adds email). A channel owns its own transport and failure handling;
 * {@link NotificationService} only fans out, so adding a channel (IM bot, SMS)
 * never touches the events themselves.
 *
 * <p>Implementations must be safe to call from concurrent login / sync
 * threads and must never throw checked exceptions from {@code deliver} —
 * {@link NotificationService} additionally guards every call.</p>
 */
public interface NotifyChannel {

    /** Contact type this channel handles, e.g. "webhook" / "email". */
    String type();

    /** True when this contact carries everything the channel needs to send. */
    boolean ready(NotifyProperties.Contact contact);

    /**
     * Deliver one event.
     *
     * @param contact destination configuration from {@code auth.notify.contacts[]}
     * @param event   short machine name, e.g. "account.locked"
     * @param body    event payload already merged with {@code event}/{@code occurredAt}
     */
    void deliver(NotifyProperties.Contact contact, String event, Map<String, Object> body) throws Exception;
}
