package io.github.shiyi.auth.server.app;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.shiyi.auth.common.api.ApiResult;
import io.github.shiyi.auth.common.api.ErrorCode;
import io.github.shiyi.auth.server.auth.JsonResponses;
import io.github.shiyi.auth.server.auth.PlatformUser;

/**
 * Blocks the OIDC authorize entry point when the signed-in user is not allowed
 * to open the requesting application.
 *
 * <p>Runs inside the authorization-server filter chain (after the security
 * context is loaded, before the protocol endpoints), so the principal is
 * already resolved. Anonymous requests are passed through — the chain redirects
 * them to the login page itself, and the check re-runs on the way back.</p>
 *
 * <p>Browser navigations are redirected to the SPA's {@code /403} page with a
 * reason code; non-HTML callers receive a JSON 403.</p>
 */
public class AppAccessFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AppAccessFilter.class);

    private final ObjectProvider<AppRegistryService> registry;
    private final ObjectMapper objectMapper;
    private final String forbiddenRedirect;

    public AppAccessFilter(ObjectProvider<AppRegistryService> registry, ObjectMapper objectMapper,
                           String forbiddenRedirect) {
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.forbiddenRedirect = forbiddenRedirect;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !("/oauth2/authorize".equals(uri) || "/oauth2/device_authorization".equals(uri));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String clientId = request.getParameter("client_id");
        if (clientId == null || clientId.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }
        AppRegistryService service = registry.getIfAvailable();
        if (service == null || !(authentication.getPrincipal() instanceof PlatformUser user)) {
            chain.doFilter(request, response);
            return;
        }

        String reason = service.denyReason(user.id(), clientId);
        if (reason == null) {
            chain.doFilter(request, response);
            return;
        }

        log.info("Denied application access: user={} client_id={} reason={}",
                user.getUsername(), clientId, reason);
        if (acceptsHtml(request)) {
            response.sendRedirect(forbiddenRedirect + "?reason=" + reason + "&app="
                    + URLEncoder.encode(clientId, StandardCharsets.UTF_8));
            return;
        }
        JsonResponses.write(response, objectMapper, HttpStatus.FORBIDDEN,
                ApiResult.fail(ErrorCode.FORBIDDEN,
                        "APP_DISABLED".equals(reason)
                                ? "Application is disabled"
                                : "Your roles do not grant access to this application"));
    }

    private boolean acceptsHtml(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }
}
