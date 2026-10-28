package io.github.shiyi.auth.server.web;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.shiyi.auth.common.api.ApiResult;
import io.github.shiyi.auth.server.auth.PlatformUser;

/**
 * Authentication endpoints for the SPA.
 *
 * <p>Login ({@code POST /api/auth/login}) and logout ({@code POST /api/auth/logout})
 * are handled by filters registered on the security chain — see SecurityConfig.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * Materializes the CSRF token and returns it together with the cookie/header
     * names. The SPA calls this once per page load before any POST.
     */
    @GetMapping("/csrf")
    public ApiResult<Map<String, String>> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return ApiResult.ok(Map.of(
                "headerName", token.getHeaderName(),
                "parameterName", token.getParameterName(),
                "token", token.getTokenValue()));
    }

    /** Current session identity: user identity + authority codes. */
    @GetMapping("/me")
    public ApiResult<Map<String, Object>> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        Map<String, Object> data;
        if (auth != null && auth.getPrincipal() instanceof PlatformUser user) {
            data = Map.of(
                    "id", user.id(),
                    "username", user.getUsername(),
                    "realName", user.realName(),
                    "authorities", authorities(auth));
        } else {
            data = Map.of(
                    "username", String.valueOf(auth != null ? auth.getPrincipal() : "anonymous"),
                    "authorities", authorities(auth));
        }
        return ApiResult.ok(data);
    }

    private List<String> authorities(Authentication auth) {
        if (auth == null) {
            return List.of();
        }
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
