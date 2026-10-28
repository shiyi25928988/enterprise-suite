package io.github.shiyi.auth.server.auth.wecom;

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

import io.github.shiyi.auth.server.auth.PlatformUser;
import io.github.shiyi.auth.user.account.UserAccountService;

/**
 * WeCom (企业微信) QR scan-login provider.
 *
 * <p>Flow: SPA receives the OAuth {@code code} at /wecom-callback and posts it
 * to /api/auth/wecom/login; this provider exchanges it for the WeCom userid,
 * resolves the local account (bind-or-username-match), and — on success —
 * audits the login and returns the same session principal as password login,
 * so the OIDC authorize flow continues seamlessly.</p>
 */
public class WeComAuthenticationProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(WeComAuthenticationProvider.class);

    private final WeComClient wecom;
    private final UserDetailsService userDetailsService;
    private final UserAccountService accounts;

    public WeComAuthenticationProvider(WeComClient wecom, UserDetailsService userDetailsService,
                                       UserAccountService accounts) {
        this.wecom = wecom;
        this.userDetailsService = userDetailsService;
        this.accounts = accounts;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String code = ((WeComAuthenticationToken) authentication).getCode();
        String ip = remoteAddress(authentication);

        String wecomUserid;
        try {
            wecomUserid = wecom.useridFromCode(code);
        } catch (Exception e) {
            log.warn("WeCom code exchange failed: {}", e.getMessage());
            accounts.onLoginFailure("WECOM", "wecom:" + code, null, "code exchange failed", ip, "n/a");
            throw new BadCredentialsException("WeCom login failed");
        }

        var account = accounts.findForWecomLogin(wecomUserid)
                .orElseThrow(() -> {
                    accounts.onLoginFailure("WECOM", wecomUserid, null,
                            "no bound local account", ip, "n/a");
                    return new BadCredentialsException(
                            "WeCom account is not linked to a platform user: " + wecomUserid);
                });

        UserDetails user = userDetailsService.loadUserByUsername(account.username());
        if (!user.isEnabled()) {
            accounts.onLoginFailure("WECOM", account.username(), account.id(),
                    "account disabled", ip, "n/a");
            throw new DisabledException("Account disabled");
        }
        if (!user.isAccountNonLocked()) {
            throw new LockedException("Account locked");
        }

        accounts.onLoginSuccess("WECOM", account.username(), account.id(), ip, "n/a");
        log.info("WeCom scan login OK: {} (wecom userid {})", account.username(), wecomUserid);

        return UsernamePasswordAuthenticationToken.authenticated(user, null,
                io.github.shiyi.auth.server.auth.AuthenticationFactors.of(
                        user, io.github.shiyi.auth.server.auth.AuthenticationFactors.OTT));
    }

    private String remoteAddress(Authentication authentication) {
        return authentication.getDetails() instanceof WebAuthenticationDetails details
                ? details.getRemoteAddress() : "unknown";
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return WeComAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
