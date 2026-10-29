package io.github.shiyi.auth.server.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import io.github.shiyi.auth.user.account.UserAccountService;

/**
 * Session attributes stamped at every successful login (password, MFA, WeCom).
 *
 * <p>Keeping the must-change flag in the session — instead of querying
 * {@code sys_user} on each request — means the guard costs zero database
 * round trips. The flag is refreshed at login and cleared on password change;
 * admin resets revoke the sessions, so a stale {@code false} cannot survive.</p>
 */
public final class LoginSessionSupport {

    public static final String ATTR_MUST_CHANGE_PASSWORD = "auth.mustChangePassword";
    public static final String ATTR_LOGIN_IP = "auth.loginIp";
    public static final String ATTR_LOGIN_AGENT = "auth.loginUserAgent";
    public static final String ATTR_LOGIN_AT = "auth.loginAt";

    private LoginSessionSupport() {
    }

    public static void stamp(HttpServletRequest request, UserAccountService accounts,
                             PlatformUser user) {
        HttpSession session = request.getSession(true);
        // Password past its configured maximum age funnels through the same
        // forced-change flow as an initial password (DB flag + session stamp).
        if (accounts.isPasswordExpired(user.id())) {
            accounts.markMustChangePassword(user.id());
        }
        session.setAttribute(ATTR_MUST_CHANGE_PASSWORD, accounts.mustChangePassword(user.id()));
        session.setAttribute(ATTR_LOGIN_IP, clientIp(request));
        session.setAttribute(ATTR_LOGIN_AGENT, abbreviate(request.getHeader("User-Agent"), 200));
        session.setAttribute(ATTR_LOGIN_AT, System.currentTimeMillis());
    }

    /** Marks the current session as compliant after a successful password change. */
    public static void clearMustChange(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(ATTR_MUST_CHANGE_PASSWORD, Boolean.FALSE);
        }
    }

    /** Resolves the client address, preferring the proxy headers used in deployments. */
    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    public static String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
