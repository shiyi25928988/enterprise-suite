package io.github.shiyi.auth.user.notify;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Notification wiring. The service bean is unconditional — with no contacts
 * configured it is a zero-cost no-op, so integration points can hold it
 * without making the feature a dependency.
 *
 * <p>Channels are plain objects rather than beans: the email channel needs
 * {@code auth.notify.mail.*} which lives in the same properties object, and
 * the list is ordered (webhook first, matching the original M10 behaviour).</p>
 */
@Configuration
@EnableConfigurationProperties(NotifyProperties.class)
public class NotifyConfig {

    @Bean
    NotificationService notificationService(NotifyProperties props) {
        List<NotifyChannel> channels = List.of(
                new WebhookChannel(),
                new EmailChannel(props.getMail()));
        return new NotificationService(props, channels);
    }
}
