package io.github.shiyi.auth.server.session;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.shiyi.auth.common.api.ApiResult;

/**
 * Online-session administration (ADMIN).
 *
 * <ul>
 *   <li>GET    /api/v1/sessions                 — active sessions</li>
 *   <li>DELETE /api/v1/sessions/{sessionId}     — kick one session</li>
 *   <li>DELETE /api/v1/sessions/users/{username}— kick every session of an account</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionAdminService sessions;

    public SessionController(SessionAdminService sessions) {
        this.sessions = sessions;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<SessionAdminService.SessionList> list(
            @RequestParam(defaultValue = "100") int limit,
            HttpServletRequest request) {
        return ApiResult.ok(sessions.list(currentSessionId(request), Math.min(limit, 500)));
    }

    @DeleteMapping("/{sessionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<Integer> revoke(@PathVariable String sessionId) {
        return ApiResult.ok(sessions.revokeSession(sessionId));
    }

    @DeleteMapping("/users/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<Integer> revokeUser(@PathVariable String username) {
        return ApiResult.ok(sessions.revokeUser(username, null));
    }

    private String currentSessionId(HttpServletRequest request) {
        return request.getSession(false) == null ? null : request.getSession(false).getId();
    }
}
