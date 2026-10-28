package io.github.shiyi.auth.server.auth;

import java.io.IOException;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Accepts {@code POST /api/auth/login} with a JSON body
 * ({@code {"username": "...", "password": "...", "redirect": "..."}})
 * for the frontend-separated login SPA.
 *
 * <p>IMPORTANT: since Spring Security 6 this filter no longer persists the
 * security context to the HTTP session by default — the
 * {@code setSecurityContextRepository(...)} call in the constructor is
 * mandatory, otherwise login "succeeds" but the session stays empty.</p>
 */
public class JsonUsernamePasswordFilter extends AbstractAuthenticationProcessingFilter {

    private static final AntPathRequestMatcher MATCHER =
            new AntPathRequestMatcher("/api/auth/login", HttpMethod.POST.name());

    private final ObjectMapper objectMapper;

    public JsonUsernamePasswordFilter(org.springframework.security.authentication.AuthenticationManager authenticationManager,
                                      ObjectMapper objectMapper,
                                      AuthenticationSuccessHandler successHandler,
                                      AuthenticationFailureHandler failureHandler) {
        super(MATCHER);
        setAuthenticationManager(authenticationManager);
        setAuthenticationSuccessHandler(successHandler);
        setAuthenticationFailureHandler(failureHandler);
        // Mandatory since Spring Security 6: persist the context into the HTTP session.
        setSecurityContextRepository(new HttpSessionSecurityContextRepository());
        this.objectMapper = objectMapper;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException, IOException {
        LoginRequest body = objectMapper.readValue(request.getInputStream(), LoginRequest.class);
        request.setAttribute(LoginRequest.REQUEST_ATTR, body);

        UsernamePasswordAuthenticationToken token = UsernamePasswordAuthenticationToken
                .unauthenticated(body.username(), body.password());
        token.setDetails(authenticationDetailsSource.buildDetails(request));
        return getAuthenticationManager().authenticate(token);
    }
}
