package io.github.shiyi.auth.user.notify;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.shiyi.auth.user.mapper.SysMessageMapper;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Notification wiring. The service bean is unconditional — with no contacts
 * configured it is a zero-cost no-op, so integration points can hold it
 * without making the feature a dependency.
 *
 * <p>Channels are plain objects rather than beans: the email channel needs
 * {@code auth.notify.mail.*} which lives in the same properties object, and
 * the list is ordered (webhook first, matching the original M10 behaviour).
 * M11 appends the inbox channel last — and only when a DataSource exists:
 * without persistence there is nowhere to land a message, so in-memory mode
 * simply has no inbox.</p>
 */
@Configuration
@EnableConfigurationProperties(NotifyProperties.class)
public class NotifyConfig {

    @Bean
    NotificationService notificationService(NotifyProperties props,
            ObjectProvider<SysMessageMapper> messageMapper,
            ObjectProvider<JdbcTemplate> jdbc) {
        List<NotifyChannel> channels = new ArrayList<>();
        channels.add(new WebhookChannel());
        channels.add(new EmailChannel(props.getMail()));
        SysMessageMapper mapper = messageMapper.getIfAvailable();
        JdbcTemplate template = jdbc.getIfAvailable();
        if (mapper != null && template != null) {
            channels.add(new InboxChannel(mapper, template));
        }
        return new NotificationService(props, channels);
    }
}
