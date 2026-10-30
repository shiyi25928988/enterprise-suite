package io.github.shiyi.auth.user.notify;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

/**
 * M10: publishes platform events to configured contact points. Currently
 * webhook only (JSON POST) — Grafana already covers email/chat channels via
 * its own contact points, so this is for in-app business events that a
 * downstream system wants to react to (auto-lock, sync outcome, …).
 *
 * <p>Delivery is synchronous but bounded (3 s connect / 3 s request) and
 * every failure path is swallowed with a warn log: a dead webhook must not
 * fail a login or a sync run.</p>
 */
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotifyProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public NotificationService(NotifyProperties props) {
        this.props = props;
    }

    /** @return true when at least one active webhook is configured */
    public boolean hasContacts() {
        return props.getContacts().stream().anyMatch(c -> !c.isDisabled()
                && "webhook".equalsIgnoreCase(c.getType())
                && c.getUrl() != null && !c.getUrl().isBlank());
    }

    /**
     * Fan out one event to every active webhook contact.
     *
     * @param event   short machine name, e.g. "account.locked", "sync.completed"
     * @param payload event specifics; a "source" identifier goes in first
     */
    public void publish(String event, Map<String, Object> payload) {
        if (!hasContacts()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", event);
        body.put("occurredAt", Instant.now().toString());
        if (payload != null) {
            body.putAll(payload);
        }
        String json = mapper.writeValueAsString(body);
        for (NotifyProperties.Contact c : props.getContacts()) {
            if (c.isDisabled() || !"webhook".equalsIgnoreCase(c.getType())
                    || c.getUrl() == null || c.getUrl().isBlank()) {
                continue;
            }
            try {
                HttpResponse<String> resp = http.send(HttpRequest.newBuilder()
                                .uri(URI.create(c.getUrl()))
                                .timeout(Duration.ofSeconds(3))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(json))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 300) {
                    log.warn("notify webhook {} returned HTTP {} for event {}",
                            c.getUrl(), resp.statusCode(), event);
                }
            } catch (Exception e) {
                log.warn("notify webhook {} failed for event {}: {}",
                        c.getUrl(), event, e.toString());
            }
        }
    }
}
