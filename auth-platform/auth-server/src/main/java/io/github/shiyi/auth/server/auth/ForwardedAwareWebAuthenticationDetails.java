package io.github.shiyi.auth.server.auth;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/**
 * M5c: authentication details that honor proxy headers
 * ({@code X-Forwarded-For} / {@code X-Real-IP}, see
 * {@link LoginSessionSupport#clientIp}) instead of the raw
 * {@code getRemoteAddr()} — behind nginx the raw address is the proxy, which
 * would make the anomaly detector's location rule and the audit trail useless.
 *
 * <p>Used by the JSON login filter (via {@link #SOURCE}) and stamped onto the
 * tokens of the QR/badge controllers, so every login type audits the same
 * client address.</p>
 */
public class ForwardedAwareWebAuthenticationDetails extends WebAuthenticationDetails {

    public static final AuthenticationDetailsSource<HttpServletRequest, WebAuthenticationDetails> SOURCE =
            ForwardedAwareWebAuthenticationDetails::new;

    private final String clientIp;

    public ForwardedAwareWebAuthenticationDetails(HttpServletRequest request) {
        super(request);
        this.clientIp = LoginSessionSupport.clientIp(request);
    }

    @Override
    public String getRemoteAddress() {
        return clientIp;
    }
}
