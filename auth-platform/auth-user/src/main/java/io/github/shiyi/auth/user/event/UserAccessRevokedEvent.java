package io.github.shiyi.auth.user.event;

/**
 * Raised when an account must lose its live sessions immediately: password
 * reset by an administrator, or the account disabled/locked.
 *
 * <p>The user module has no knowledge of the session store (Redis lives in the
 * server module), so it only announces the fact — the server module listens and
 * drops the sessions.</p>
 *
 * @param username account whose sessions must be revoked
 * @param reason   short audit-friendly reason (e.g. {@code password-reset})
 */
public record UserAccessRevokedEvent(String username, String reason) {
}
