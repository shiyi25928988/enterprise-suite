package io.github.shiyi.auth.server.auth.badge;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/** Pre-authentication token carrying the scanned badge (工牌) code. */
public class BadgeAuthenticationToken extends AbstractAuthenticationToken {

    private final String badgeCode;

    public BadgeAuthenticationToken(String badgeCode) {
        super(List.<GrantedAuthority>of());
        this.badgeCode = badgeCode;
        setAuthenticated(false);
    }

    public String getBadgeCode() { return badgeCode; }

    @Override public Object getCredentials() { return null; }
    @Override public Object getPrincipal() { return badgeCode; }
}
