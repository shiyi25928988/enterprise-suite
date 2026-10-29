package io.github.shiyi.auth.server.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.shiyi.auth.common.api.ApiResult;
import io.github.shiyi.auth.common.api.ErrorCode;
import io.github.shiyi.auth.common.exception.BizException;
import io.github.shiyi.auth.server.auth.LoginSessionSupport;
import io.github.shiyi.auth.server.auth.PlatformUser;
import io.github.shiyi.auth.server.session.SessionAdminService;
import io.github.shiyi.auth.user.account.UserAccountService;
import io.github.shiyi.auth.user.util.PasswordPolicy;

/**
 * Authentication endpoints for the SPA.
 *
 * <p>Login ({@code POST /api/auth/login}) and logout ({@code POST /api/auth/logout})
 * are handled by filters registered on the security chain — see SecurityConfig.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserAccountService accounts;
    private final SessionAdminService sessions;

    public AuthController(UserAccountService accounts, SessionAdminService sessions) {
        this.accounts = accounts;
        this.sessions = sessions;
    }

    /**
     * Materializes the CSRF token and returns it together with the cookie/header
     * names. The SPA calls this once per page load before any POST.
     *
     * <p>IMPORTANT: the {@code CsrfToken} request attribute is the BREACH-masked
     * value (only safe for HTML rendering) while the repository/cookie holds the
     * raw value the server actually compares against. Returning the masked value
     * makes every header-based client fail with 403, so this endpoint unwraps the
     * {@link DeferredCsrfToken} instead.</p>
     */
    @GetMapping("/csrf")
    public ApiResult<Map<String, String>> csrf(HttpServletRequest request) {
        DeferredCsrfToken deferred =
                (DeferredCsrfToken) request.getAttribute(DeferredCsrfToken.class.getName());
        CsrfToken token = deferred != null
                ? deferred.get()
                : (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return ApiResult.ok(Map.of(
                "headerName", token.getHeaderName(),
                "parameterName", token.getParameterName(),
                "token", token.getToken()));
    }

    /** Current session identity: user identity + authority codes + password state. */
    @GetMapping("/me")
    public ApiResult<Map<String, Object>> me(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        Map<String, Object> data = new LinkedHashMap<>();
        if (auth != null && auth.getPrincipal() instanceof PlatformUser user) {
            data.put("id", user.id());
            data.put("username", user.getUsername());
            data.put("realName", user.realName());
            data.put("authorities", authorities(auth));
            data.put("mustChangePassword", mustChangePassword(request));
        } else {
            data.put("username", String.valueOf(auth != null ? auth.getPrincipal() : "anonymous"));
            data.put("authorities", authorities(auth));
        }
        return ApiResult.ok(data);
    }

    public record ChangePasswordRequest(String oldPassword, String newPassword) {
    }

    /**
     * Self-service password change. Every other session of the account is
     * revoked, so a stolen session cannot outlive a password reset.
     */
    @PostMapping("/password")
    public ApiResult<Void> changePassword(@RequestBody ChangePasswordRequest body,
                                          HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof PlatformUser me)) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        if (body == null || body.newPassword() == null || body.newPassword().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "新密码不能为空");
        }
        if (!accounts.matchesPassword(me.id(), body.oldPassword())) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS, "当前密码不正确");
        }
        if (body.newPassword().equals(body.oldPassword())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "新密码不能与当前密码相同");
        }
        PasswordPolicy.validate(body.newPassword());
        accounts.changePassword(me.id(), body.newPassword());

        LoginSessionSupport.clearMustChange(request);
        String current = request.getSession(false) == null ? null : request.getSession(false).getId();
        sessions.revokeUser(me.getUsername(), current);
        return ApiResult.ok();
    }

    private boolean mustChangePassword(HttpServletRequest request) {
        var session = request.getSession(false);
        return session != null
                && Boolean.TRUE.equals(session.getAttribute(LoginSessionSupport.ATTR_MUST_CHANGE_PASSWORD));
    }

    private List<String> authorities(Authentication auth) {
        if (auth == null) {
            return List.of();
        }
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
