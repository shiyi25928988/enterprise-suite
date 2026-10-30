package io.github.shiyi.auth.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.shiyi.auth.user.notify.NotificationService;
import io.github.shiyi.auth.workflow.notify.WorkflowNotifier;

/**
 * Composition root for workflow events: binds the engine's outbound port to the
 * platform's notification channels (webhook / email).
 *
 * <p>This is the only place where the two domains meet, and it is one line.
 * The workflow module itself never imports {@code auth-user}, so it can be
 * lifted into its own deployment without touching a single workflow class —
 * only this adapter is replaced (by a message publish or an HTTP call).</p>
 */
@Configuration
public class WorkflowNotifyConfig {

    @Bean
    WorkflowNotifier workflowNotifier(NotificationService notifications) {
        return notifications::publish;
    }
}
