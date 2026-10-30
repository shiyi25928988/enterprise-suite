package io.github.shiyi.auth.user.notify;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Notification wiring. The service bean is unconditional — with no contacts
 * configured it is a zero-cost no-op, so integration points can hold it
 * without making the feature a dependency.
 */
@Configuration
@EnableConfigurationProperties(NotifyProperties.class)
public class NotifyConfig {

    @Bean
    NotificationService notificationService(NotifyProperties props) {
        return new NotificationService(props);
    }
}
