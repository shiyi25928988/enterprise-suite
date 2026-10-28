package io.github.shiyi.auth.server.auth;

import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * Official Spring Security recipe for single-page applications:
 *
 * <ul>
 *   <li>renders the token once (deferred) into the {@code XSRF-TOKEN} cookie</li>
 *   <li>accepts the raw token value from the {@code X-XSRF-TOKEN} header</li>
 *   <li>keeps XOR/BREACH protection for form-encoded fallbacks</li>
 * </ul>
 *
 * <p>See https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html</p>
 */
final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler
        implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       Supplier<CsrfToken> csrfToken) {
        this.delegate.handle(request, response, csrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
            return super.resolveCsrfTokenValue(request, csrfToken);
        }
        return this.delegate.resolveCsrfTokenValue(request, csrfToken);
    }
}
