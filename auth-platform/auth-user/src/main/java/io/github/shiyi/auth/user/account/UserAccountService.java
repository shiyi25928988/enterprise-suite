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

    /**
     * Resolves the account for a WeCom (企业微信) scan login.
     * Lookup order: by bound wecom_userid, then by username match (auto-binds
     * on first login — skeleton policy; tighten per HR sync rules later).
     */
    Optional<UserAccount> findForWecomLogin(String wecomUserid);

    /**
     * Resolves the account for a DingTalk (钉钉) scan login (M5c). Same
     * contract as {@link #findForWecomLogin}: bound dingtalk_userid first
     * (sync-imported users match via external_id), then username auto-bind.
     */
    Optional<UserAccount> findForDingtalkLogin(String dingtalkUserid);

    /**
     * Resolves the account holding the given badge (工牌) code (M5c) —
     * password-free login for shop-floor terminals. Random 43+ chars,
     * unique; rotation invalidates the printed QR.
     */
    Optional<UserAccount> findByBadgeCode(String badgeCode);

    /**
     * Replaces the user's badge code with a fresh random one and returns it.
     * Called from Security settings; the old printed QR stops working.
     */
    String rotateBadgeCode(Long userId);

    /** Current badge code (Security settings renders it as a QR), null when never issued. */
    String findBadgeCode(Long userId);

    /** Role codes of the user, empty when none. */
    java.util.List<String> findRoleCodes(Long userId);

    /** Persists the TOTP secret and turns the second factor on. */
    void enableMfa(Long userId, String totpSecret);

    /** Clears the TOTP secret and turns the second factor off. */
    void disableMfa(Long userId);

    /**
     * True when the holder is still on an initial password and must replace it
     * before the platform lets them work (admin-created, imported, or reset).
     */
    boolean mustChangePassword(Long userId);

    /**
     * True when the current password is older than the configured maximum age
     * (auth.password.max-age-days). Never true when the policy is disabled
     * (0) or the password has never been stamped.
     */
    boolean isPasswordExpired(Long userId);

    /** Flags the account so the next login routes through the change flow. */
    void markMustChangePassword(Long userId);

    /** Compares a raw password with the stored hash. */
    boolean matchesPassword(Long userId, String rawPassword);

    /**
     * Stores a new password (encoded), clears the must-change flag, stamps
     * password_update_time and resets the failure counter.
     */
    void changePassword(Long userId, String rawPassword);

    /** Records a successful login: audit row + clears fail counter + refreshes last_login_time. */
    void onLoginSuccess(String loginType, String username, Long userId, String ip, String userAgent);

    /**
     * Records a failed login: audit row + increments fail counter.
     * Locks the account (status=2) after 5 consecutive failures.
     *
     * @return true when the account has just been locked
     */
    boolean onLoginFailure(String loginType, String username, Long userId,
                           String failReason, String ip, String userAgent);
}
