package io.github.shiyi.auth.server.session;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.github.shiyi.auth.user.event.UserAccessRevokedEvent;

/**
 * Drops every live session of an account when the user module reports that its
 * access was rescinded (password reset / disabled / locked).
 */
@Component
public class SessionRevocationListener {

    private final SessionAdminService sessions;

    public SessionRevocationListener(SessionAdminService sessions) {
        this.sessions = sessions;
    }

    @EventListener
    public void on(UserAccessRevokedEvent event) {
        sessions.revokeUser(event.username(), null);
    }
}
