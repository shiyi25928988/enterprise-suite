package io.github.shiyi.auth.server.auth.wecom;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Pre-authentication token carrying the WeCom OAuth code
 * (principal = code, credentials = null until resolved).
 */
public class WeComAuthenticationToken extends AbstractAuthenticationToken {

    private final String code;

    public WeComAuthenticationToken(String code) {
        // Security 7 added an AbstractAuthenticationBuilder ctor, so a bare
        // null literal is ambiguous — pass an explicit empty authority list.
        super(List.<GrantedAuthority>of());
        this.code = code;
        setAuthenticated(false);
    }

    public String getCode() { return code; }

    @Override public Object getCredentials() { return null; }
    @Override public Object getPrincipal() { return code; }
}
