package io.github.shiyi.auth.server.auth;

import java.io.IOException;
import java.util.Set;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.shiyi.auth.common.api.ApiResult;
import io.github.shiyi.auth.common.api.ErrorCode;

/**
 * Blocks everything but the password-change flow while the signed-in account
 * still carries its initial secret.
 *
 * <p>Without this the "must change password" flag would only be a hint in the
 * UI: a user could keep using the portal and every downstream OIDC client with
 * the shared initial password.</p>
 */
public class MustChangePasswordFilter extends OncePerRequestFilter {

    /** Endpoints the holder needs while the flag is on. */
    private static final Set<String> ALLOWED = Set.of(
            "/api/auth/password",
            "/api/auth/me",
            "/api/auth/csrf",
            "/api/auth/logout");

    private final ObjectMapper objectMapper;

    public MustChangePasswordFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null
                || !Boolean.TRUE.equals(session.getAttribute(LoginSessionSupport.ATTR_MUST_CHANGE_PASSWORD))) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (ALLOWED.contains(path) || path.startsWith("/actuator") || path.startsWith("/assets")) {
            chain.doFilter(request, response);
            return;
        }

        if (acceptsHtml(request)) {
            // browser navigation: send them to the SPA page that handles the change
            response.sendRedirect("/password");
            return;
        }
        JsonResponses.write(response, objectMapper, HttpStatus.FORBIDDEN,
                ApiResult.fail(ErrorCode.PASSWORD_EXPIRED));
    }

    private boolean acceptsHtml(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }
}
