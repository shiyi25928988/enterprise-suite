package io.github.shiyi.auth.server.auth;

import java.io.IOException;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

/**
 * Login success: replies 200 + JSON with the URL the browser must navigate to,
 * resuming the interrupted {@code /oauth2/authorize} request.
 *
 * <p>Resolution order: explicit {@code redirect} field in the login body →
 * saved request (created by the entry point redirect) → {@code /}.</p>
 */
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final ObjectMapper objectMapper;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    public LoginSuccessHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String target = resolveRedirect(request, response);

        response.setStatus(HttpServletResponse.SC_OK);
        JsonResponses.write(response, objectMapper,
                org.springframework.http.HttpStatus.OK,
                io.github.shiyi.auth.common.api.ApiResult.ok(Map.of("redirect", target)));
    }

    private String resolveRedirect(HttpServletRequest request, HttpServletResponse response) {
        Object attr = request.getAttribute(LoginRequest.REQUEST_ATTR);
        if (attr instanceof LoginRequest body && body.redirect() != null && !body.redirect().isBlank()) {
            return body.redirect();
        }
        SavedRequest saved = requestCache.getRequest(request, response);
        if (saved != null) {
            return saved.getRedirectUrl();
        }
        return "/";
    }
}
