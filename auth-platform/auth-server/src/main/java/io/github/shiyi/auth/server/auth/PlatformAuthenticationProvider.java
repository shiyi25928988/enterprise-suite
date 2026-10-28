package io.github.shiyi.auth.server.auth;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Username + password authentication provider.
 *
 * <p>Deliberately hand-written (not DaoAuthenticationProvider) to keep the
 * extension point explicit: M5 will add LDAP / WeCom-QR / badge-QR providers
 * beside this one inside the same {@code ProviderManager}.</p>
 */
public class PlatformAuthenticationProvider implements AuthenticationProvider {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public PlatformAuthenticationProvider(UserDetailsService userDetailsService,
                                          PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = String.valueOf(authentication.getPrincipal());
        String password = String.valueOf(authentication.getCredentials());

        UserDetails user = userDetailsService.loadUserByUsername(username);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        if (!user.isEnabled()) {
            throw new org.springframework.security.authentication.DisabledException("Account disabled");
        }
        if (!user.isAccountNonLocked()) {
            throw new org.springframework.security.authentication.LockedException("Account locked");
        }

        return UsernamePasswordAuthenticationToken.authenticated(
                user, null, user.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
