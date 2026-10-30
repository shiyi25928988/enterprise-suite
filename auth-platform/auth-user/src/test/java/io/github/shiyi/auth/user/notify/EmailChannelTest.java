package io.github.shiyi.auth.user.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Email contact point: the readiness gate is what keeps a half-configured mail
 * block from logging a failure on every login, and the body renderer is what
 * an on-call engineer actually reads at 3am.
 */
class EmailChannelTest {

    private static NotifyProperties.Mail mailHost(String host) {
        NotifyProperties.Mail mail = new NotifyProperties.Mail();
        mail.setHost(host);
        return mail;
    }

    private static NotifyProperties.Contact to(String recipient) {
        NotifyProperties.Contact c = new NotifyProperties.Contact();
        c.setType("email");
        c.setTo(recipient);
        return c;
    }

    @Test
    void readyNeedsBothARelayAndARecipient() {
        EmailChannel configured = new EmailChannel(mailHost("smtp.example.com"));
        EmailChannel noRelay = new EmailChannel(mailHost("  "));
        EmailChannel unconfigured = new EmailChannel(new NotifyProperties.Mail());

        assertThat(configured.ready(to("ops@example.com"))).isTrue();
        assertThat(configured.ready(to(""))).isFalse();
        assertThat(configured.ready(to(null))).isFalse();
        assertThat(noRelay.ready(to("ops@example.com"))).isFalse();
        assertThat(unconfigured.ready(to("ops@example.com"))).isFalse();
    }

    @Test
    void renderWritesOneFieldPerLineInPayloadOrder() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", "sync.completed");
        body.put("occurredAt", "2026-10-30T05:02:15Z");
        body.put("source", "ldap");
        body.put("usersCreated", 2);

        assertThat(EmailChannel.render(body)).isEqualTo("""
                event: sync.completed
                occurredAt: 2026-10-30T05:02:15Z
                source: ldap
                usersCreated: 2
                """);
    }

    @Test
    void renderToleratesEmptyBody() {
        assertThat(EmailChannel.render(Map.of())).isEmpty();
    }

    @Test
    void channelTypeIsEmail() {
        assertThat(new EmailChannel(mailHost("smtp.example.com")).type()).isEqualTo("email");
    }
}
