package io.github.shiyi.auth.user.notify;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

/**
 * JSON POST to a URL — the original M10 contact type, unchanged in behaviour:
 * 3 s connect / 3 s request, non-2xx logged as a warning.
 */
public class WebhookChannel implements NotifyChannel {

    private static final Logger log = LoggerFactory.getLogger(WebhookChannel.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Override
    public String type() {
        return "webhook";
    }

    @Override
    public boolean ready(NotifyProperties.Contact contact) {
        return contact.getUrl() != null && !contact.getUrl().isBlank();
    }

    @Override
    public void deliver(NotifyProperties.Contact contact, String event, Map<String, Object> body)
            throws Exception {
        HttpResponse<String> resp = http.send(HttpRequest.newBuilder()
                        .uri(URI.create(contact.getUrl()))
                        .timeout(Duration.ofSeconds(3))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300) {
            log.warn("notify webhook {} returned HTTP {} for event {}",
                    contact.getUrl(), resp.statusCode(), event);
        }
    }
}
