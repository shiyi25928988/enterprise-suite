package io.github.shiyi.auth.server.auth.wecom;

import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * Pre-authentication token carrying the WeCom OAuth code
 * (principal = code, credentials = null until resolved).
 */
public class WeComAuthenticationToken extends AbstractAuthenticationToken {

    private final String code;

    public WeComAuthenticationToken(String code) {
        super(null);
        this.code = code;
        setAuthenticated(false);
    }

    public String getCode() { return code; }

    @Override public Object getCredentials() { return null; }
    @Override public Object getPrincipal() { return code; }
}
