package io.github.shiyi.auth.user.notify;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * M10: publishes platform events to configured contact points. M10b splits the
 * transports into {@link NotifyChannel}s (webhook, email) while keeping this
 * entry point unchanged — callers still just call {@code publish(event, map)}.
 *
 * <p>Delivery is synchronous so the event ordering is the business ordering,
 * but every transport call is bounded by its own timeouts and every failure is
 * swallowed with a warn log: a dead webhook or an unreachable relay must never
 * fail a login or a sync run.</p>
 */
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotifyProperties props;
    private final List<NotifyChannel> channels;

    public NotificationService(NotifyProperties props, List<NotifyChannel> channels) {
        this.props = props;
        this.channels = List.copyOf(channels);
    }

    /** @return true when at least one contact has a ready channel behind it */
    public boolean hasContacts() {
        return props.getContacts().stream()
                .anyMatch(c -> !c.isDisabled() && channelFor(c) != null);
    }

    /**
     * Fan out one event to every active contact whose channel can send.
     *
     * @param event   short machine name, e.g. "account.locked", "sync.completed"
     * @param payload event specifics; a "source" identifier goes in first
     */
    public void publish(String event, Map<String, Object> payload) {
        if (channels.isEmpty() || props.getContacts().isEmpty()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", event);
        body.put("occurredAt", Instant.now().toString());
        if (payload != null) {
            body.putAll(payload);
        }
        for (NotifyProperties.Contact c : props.getContacts()) {
            if (c.isDisabled()) {
                continue;
            }
            NotifyChannel channel = channelFor(c);
            if (channel == null) {
                log.debug("notify contact type '{}' has no ready channel, skipped", c.getType());
                continue;
            }
            try {
                channel.deliver(c, event, body);
            } catch (Exception e) {
                log.warn("notify {} contact {} failed for event {}: {}",
                        channel.type(), describe(c), event, e.toString());
            }
        }
    }

    /** Matching channel, or null when the type is unknown or the contact is incomplete. */
    private NotifyChannel channelFor(NotifyProperties.Contact contact) {
        String type = contact.getType();
        if (type == null || type.isBlank()) {
            return null;
        }
        for (NotifyChannel channel : channels) {
            if (channel.type().equalsIgnoreCase(type) && channel.ready(contact)) {
                return channel;
            }
        }
        return null;
    }

    private static String describe(NotifyProperties.Contact c) {
        return "webhook".equalsIgnoreCase(c.getType()) ? c.getUrl() : c.getTo();
    }
}
