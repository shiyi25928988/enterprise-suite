package io.github.shiyi.auth.user.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

/**
 * Channel fan-out contract: an event reaches every ready contact, one broken
 * transport never suppresses the next one, and an unready transport (email
 * without a relay) is skipped silently instead of failing the caller.
 */
class NotificationServiceTest {

    /** Minimal channel that records what it was asked to deliver. */
    private static final class RecordingChannel implements NotifyChannel {
        private final String type;
        private final Predicate<NotifyProperties.Contact> readiness;
        private final List<String> events = new ArrayList<>();
        private final List<Map<String, Object>> bodies = new ArrayList<>();
        private RuntimeException failure;

        RecordingChannel(String type) {
            this(type, c -> true);
        }

        RecordingChannel(String type, Predicate<NotifyProperties.Contact> readiness) {
            this.type = type;
            this.readiness = readiness;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public boolean ready(NotifyProperties.Contact contact) {
            return readiness.test(contact);
        }

        @Override
        public void deliver(NotifyProperties.Contact contact, String event, Map<String, Object> body) {
            if (failure != null) {
                throw failure;
            }
            events.add(event);
            bodies.add(body);
        }
    }

    private static NotifyProperties.Contact contact(String type) {
        NotifyProperties.Contact c = new NotifyProperties.Contact();
        c.setType(type);
        c.setUrl("http://hooks.example.com/" + type);
        c.setTo("ops@example.com");
        return c;
    }

    private static NotifyProperties props(NotifyProperties.Contact... contacts) {
        NotifyProperties p = new NotifyProperties();
        p.setContacts(new ArrayList<>(List.of(contacts)));
        return p;
    }

    @Test
    void eventReachesEveryReadyContactWithEventAndTimestampFirst() {
        RecordingChannel webhook = new RecordingChannel("webhook");
        RecordingChannel email = new RecordingChannel("email");
        NotificationService service = new NotificationService(
                props(contact("webhook"), contact("email")), List.of(webhook, email));

        // LinkedHashMap: the email body renders in payload order, so the test pins it
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("source", "ldap");
        payload.put("usersCreated", 2);
        service.publish("sync.completed", payload);

        assertThat(webhook.events).containsExactly("sync.completed");
        assertThat(email.events).containsExactly("sync.completed");
        assertThat(webhook.bodies.get(0))
                .containsEntry("event", "sync.completed")
                .containsEntry("source", "ldap")
                .containsEntry("usersCreated", 2)
                .containsKey("occurredAt");
        // envelope keys come first, payload after — the mail body is rendered in map order
        assertThat(webhook.bodies.get(0).keySet())
                .containsExactly("event", "occurredAt", "source", "usersCreated");
    }

    @Test
    void disabledContactIsSkipped() {
        RecordingChannel webhook = new RecordingChannel("webhook");
        NotifyProperties.Contact off = contact("webhook");
        off.setDisabled(true);
        NotificationService service = new NotificationService(props(off), List.of(webhook));

        service.publish("account.locked", Map.of("username", "zhangsan"));

        assertThat(webhook.events).isEmpty();
        assertThat(service.hasContacts()).isFalse();
    }

    @Test
    void unreadyChannelIsSkippedWithoutFailingTheOthers() {
        RecordingChannel webhook = new RecordingChannel("webhook");
        // stands in for "email configured but no mail host": ready() -> false
        RecordingChannel email = new RecordingChannel("email", c -> false);
        NotificationService service = new NotificationService(
                props(contact("webhook"), contact("email")), List.of(webhook, email));

        service.publish("login.anomaly", Map.of("type", "new_location"));

        assertThat(webhook.events).containsExactly("login.anomaly");
        assertThat(email.events).isEmpty();
        assertThat(service.hasContacts()).isTrue();
    }

    @Test
    void unknownContactTypeIsIgnored() {
        RecordingChannel webhook = new RecordingChannel("webhook");
        NotificationService service = new NotificationService(
                props(contact("smtp-legacy")), List.of(webhook));

        service.publish("sync.failed", Map.of("source", "wecom"));

        assertThat(webhook.events).isEmpty();
    }

    @Test
    void oneFailingTransportDoesNotStopTheRest() {
        RecordingChannel broken = new RecordingChannel("webhook");
        broken.failure = new IllegalStateException("connection refused");
        RecordingChannel email = new RecordingChannel("email");
        NotificationService service = new NotificationService(
                props(contact("webhook"), contact("email")), List.of(broken, email));

        service.publish("task.overdue", Map.of("taskId", "42"));

        assertThat(email.events).containsExactly("task.overdue");
    }

    @Test
    void noContactsMeansNoWorkAndNullPayloadIsTolerated() {
        RecordingChannel webhook = new RecordingChannel("webhook");
        NotificationService empty = new NotificationService(new NotifyProperties(), List.of(webhook));

        empty.publish("account.locked", null);

        assertThat(webhook.events).isEmpty();
        assertThat(empty.hasContacts()).isFalse();

        NotificationService service = new NotificationService(
                props(contact("webhook")), List.of(webhook));
        service.publish("account.locked", null);
        assertThat(webhook.bodies.get(0))
                .containsEntry("event", "account.locked")
                .containsKey("occurredAt");
    }
}
