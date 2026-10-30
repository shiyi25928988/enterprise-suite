package io.github.shiyi.auth.server.auth.dingtalk;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/** Pre-authentication token carrying the DingTalk QR-login OAuth code. */
public class DingTalkAuthenticationToken extends AbstractAuthenticationToken {

    private final String code;

    public DingTalkAuthenticationToken(String code) {
        // Security 7: bare null literal is ambiguous — explicit empty list
        super(List.<GrantedAuthority>of());
        this.code = code;
        setAuthenticated(false);
    }

    public String getCode() { return code; }

    @Override public Object getCredentials() { return null; }
    @Override public Object getPrincipal() { return code; }
}
