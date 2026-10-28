package io.github.shiyi.auth.server.auth;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import io.github.shiyi.auth.user.account.UserAccountService;

/**
 * Username + password authentication provider with login auditing and auto-lock.
 *
 * <p>Deliberately hand-written (not DaoAuthenticationProvider) to keep the
 * extension point explicit: M5 will add LDAP / WeCom-QR / badge-QR providers
 * beside this one inside the same {@code ProviderManager}.</p>
 */
public class PlatformAuthenticationProvider implements AuthenticationProvider {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final UserAccountService accounts;
    private final io.github.shiyi.auth.server.auth.mfa.MfaChallengeService mfaChallenges;

    public PlatformAuthenticationProvider(UserDetailsService userDetailsService,
                                          PasswordEncoder passwordEncoder,
                                          UserAccountService accounts,
                                          io.github.shiyi.auth.server.auth.mfa.MfaChallengeService mfaChallenges) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.accounts = accounts;
        this.mfaChallenges = mfaChallenges;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = String.valueOf(authentication.getPrincipal());
        String password = String.valueOf(authentication.getCredentials());
        String ip = remoteAddress(authentication);
        String userAgent = "n/a";

        UserDetails user;
        try {
            user = userDetailsService.loadUserByUsername(username);
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            // Do NOT reveal whether the account exists: same error for both cases.
            accounts.onLoginFailure("PASSWORD", username, null, "user not found", ip, userAgent);
            throw new BadCredentialsException("Invalid username or password");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            boolean justLocked = accounts.onLoginFailure("PASSWORD", username, userIdOf(user),
                    "bad credentials", ip, userAgent);
            throw new BadCredentialsException(justLocked
                    ? "Account locked after too many failed attempts"
                    : "Invalid username or password");
        }
        if (!user.isEnabled()) {
            accounts.onLoginFailure("PASSWORD", username, userIdOf(user), "account disabled", ip, userAgent);
            throw new DisabledException("Account disabled");
        }
        if (!user.isAccountNonLocked()) {
            throw new LockedException("Account locked");
        }

        // Password is correct — but a TOTP code is still required. No success
        // audit yet: the login is only complete in /api/auth/mfa/verify.
        if (user instanceof PlatformUser platformUser && platformUser.mfaEnabled()) {
            throw new io.github.shiyi.auth.server.auth.mfa.MfaRequiredException(
                    mfaChallenges.create(username));
        }

        accounts.onLoginSuccess("PASSWORD", username, userIdOf(user), ip, userAgent);

        // SAS 7 requires an authentication factor (source of the auth_time claim).
        return UsernamePasswordAuthenticationToken.authenticated(
                user, null,
                AuthenticationFactors.of(user, AuthenticationFactors.PASSWORD));
    }

    private Long userIdOf(UserDetails user) {
        return user instanceof PlatformUser platformUser ? platformUser.id() : null;
    }

    private String remoteAddress(Authentication authentication) {
        if (authentication.getDetails() instanceof WebAuthenticationDetails details) {
            return details.getRemoteAddress();
        }
        return "unknown";
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
