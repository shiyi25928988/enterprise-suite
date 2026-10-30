package io.github.shiyi.auth.server.auth.badge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import io.github.shiyi.auth.server.auth.AuthenticationFactors;
import io.github.shiyi.auth.user.account.UserAccountService;

/**
 * Badge (工牌) login provider (M5c): the QR printed on an employee badge holds
 * a per-user random secret; scanning it on any terminal logs the holder in
 * without a password. The secret is 43 chars, unique, and rotatable — a lost
 * badge is a rotation, not an incident.
 *
 * <p>Audited as {@code BADGE}; failure counting applies like any other login
 * type, so a badge-guessing terminal trips brute-force anomaly detection too.</p>
 */
public class BadgeAuthenticationProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(BadgeAuthenticationProvider.class);

    private final UserDetailsService userDetailsService;
    private final UserAccountService accounts;

    public BadgeAuthenticationProvider(UserDetailsService userDetailsService,
                                       UserAccountService accounts) {
        this.userDetailsService = userDetailsService;
        this.accounts = accounts;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String badgeCode = ((BadgeAuthenticationToken) authentication).getBadgeCode();
        String ip = remoteAddress(authentication);

        var account = accounts.findByBadgeCode(badgeCode)
                .orElseThrow(() -> {
                    accounts.onLoginFailure("BADGE", "badge:" + abbreviate(badgeCode), null,
                            "unknown badge code", ip, "n/a");
                    return new BadCredentialsException("Invalid badge code");
                });

        UserDetails user = userDetailsService.loadUserByUsername(account.username());
        if (!user.isEnabled()) {
            accounts.onLoginFailure("BADGE", account.username(), account.id(),
                    "account disabled", ip, "n/a");
            throw new DisabledException("Account disabled");
        }
        if (!user.isAccountNonLocked()) {
            throw new LockedException("Account locked");
        }

        accounts.onLoginSuccess("BADGE", account.username(), account.id(), ip, "n/a");
        log.info("Badge login OK: {}", account.username());

        return UsernamePasswordAuthenticationToken.authenticated(user, null,
                AuthenticationFactors.of(user, AuthenticationFactors.OTT));
    }

    /** Never audit or log the full badge secret. */
    private static String abbreviate(String code) {
        return code == null ? "" : code.length() <= 6 ? code : code.substring(0, 6) + "…";
    }

    private String remoteAddress(Authentication authentication) {
        return authentication.getDetails() instanceof WebAuthenticationDetails details
                ? details.getRemoteAddress() : "unknown";
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return BadgeAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
