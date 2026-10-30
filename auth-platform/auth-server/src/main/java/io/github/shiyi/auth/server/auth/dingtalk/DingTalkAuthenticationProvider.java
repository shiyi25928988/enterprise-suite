package io.github.shiyi.auth.server.auth.dingtalk;

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
 * DingTalk (钉钉) QR scan-login provider (M5c) — same contract as the WeCom
 * provider: exchange code → corp userid, resolve the local account (bound
 * dingtalk_userid / sync external_id / username auto-bind), audit, and return
 * the standard session principal.
 */
public class DingTalkAuthenticationProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(DingTalkAuthenticationProvider.class);

    private final DingTalkClient dingtalk;
    private final UserDetailsService userDetailsService;
    private final UserAccountService accounts;

    public DingTalkAuthenticationProvider(DingTalkClient dingtalk,
                                          UserDetailsService userDetailsService,
                                          UserAccountService accounts) {
        this.dingtalk = dingtalk;
        this.userDetailsService = userDetailsService;
        this.accounts = accounts;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String code = ((DingTalkAuthenticationToken) authentication).getCode();
        String ip = remoteAddress(authentication);

        String dingtalkUserid;
        try {
            dingtalkUserid = dingtalk.useridFromCode(code);
        } catch (Exception e) {
            log.warn("DingTalk code exchange failed: {}", e.getMessage());
            accounts.onLoginFailure("DINGTALK", "dingtalk:" + code, null,
                    "code exchange failed", ip, "n/a");
            throw new BadCredentialsException("DingTalk login failed");
        }

        var account = accounts.findForDingtalkLogin(dingtalkUserid)
                .orElseThrow(() -> {
                    accounts.onLoginFailure("DINGTALK", dingtalkUserid, null,
                            "no bound local account", ip, "n/a");
                    return new BadCredentialsException(
                            "DingTalk account is not linked to a platform user: " + dingtalkUserid);
                });

        UserDetails user = userDetailsService.loadUserByUsername(account.username());
        if (!user.isEnabled()) {
            accounts.onLoginFailure("DINGTALK", account.username(), account.id(),
                    "account disabled", ip, "n/a");
            throw new DisabledException("Account disabled");
        }
        if (!user.isAccountNonLocked()) {
            throw new LockedException("Account locked");
        }

        accounts.onLoginSuccess("DINGTALK", account.username(), account.id(), ip, "n/a");
        log.info("DingTalk scan login OK: {} (dingtalk userid {})", account.username(), dingtalkUserid);

        return UsernamePasswordAuthenticationToken.authenticated(user, null,
                AuthenticationFactors.of(user, AuthenticationFactors.OTT));
    }

    private String remoteAddress(Authentication authentication) {
        return authentication.getDetails() instanceof WebAuthenticationDetails details
                ? details.getRemoteAddress() : "unknown";
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return DingTalkAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
