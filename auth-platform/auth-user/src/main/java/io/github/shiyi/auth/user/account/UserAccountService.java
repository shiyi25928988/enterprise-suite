package io.github.shiyi.auth.user.account;

import java.util.Optional;

/**
 * Authentication-facing account service: the bridge between the security layer
 * (auth-server) and the user domain (auth-user).
 *
 * <p>Dual mode: JDBC implementation when a DataSource is configured,
 * in-memory fallback otherwise (see UserDomainConfig).</p>
 */
public interface UserAccountService {

    Optional<UserAccount> findByUsername(String username);

    /** Role codes of the user, empty when none. */
    java.util.List<String> findRoleCodes(Long userId);

    /** Records a successful login: audit row + clears fail counter + refreshes last_login_time. */
    void onLoginSuccess(String username, Long userId, String ip, String userAgent);

    /**
     * Records a failed login: audit row + increments fail counter.
     * Locks the account (status=2) after 5 consecutive failures.
     *
     * @return true when the account has just been locked
     */
    boolean onLoginFailure(String username, Long userId, String failReason, String ip, String userAgent);
}
