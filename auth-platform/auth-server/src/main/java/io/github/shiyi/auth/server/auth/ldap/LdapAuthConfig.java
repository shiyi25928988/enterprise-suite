package io.github.shiyi.auth.server.auth.ldap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import io.github.shiyi.auth.user.account.UserAccountService;

/**
 * AD / LDAP authentication (M5). Disabled by default; enable with:
 *
 * <pre>
 * auth:
 *   ldap:
 *     enabled: true
 *     domain: corp.example.com
 *     url: ldap://dc01.corp.example.com:389
 * </pre>
 *
 * <p>The AD provider validates the credentials against the Windows domain;
 * authorities are then REPLACED with our own sys_user roles, so downstream
 * authorization stays identical no matter which provider authenticated.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "auth.ldap", name = "enabled", havingValue = "true")
public class LdapAuthConfig {

    private static final Logger log = LoggerFactory.getLogger(LdapAuthConfig.class);

    @Bean
    AuthenticationProvider ldapAuthenticationProvider(org.springframework.core.env.Environment env,
                                                      UserDetailsService userDetailsService,
                                                      UserAccountService accounts) {
        String domain = env.getProperty("auth.ldap.domain");
        String url = env.getProperty("auth.ldap.url");
        ActiveDirectoryLdapAuthenticationProvider ad =
                new ActiveDirectoryLdapAuthenticationProvider(domain, url);
        ad.setConvertSubErrorCodesToExceptions(true);
        ad.setUseAuthenticationRequestCredentials(true);
        log.info("AD/LDAP authentication enabled: domain={}, url={}", domain, url);
        return new AdBackedAuthenticationProvider(ad, userDetailsService, accounts);
    }

    /**
     * Delegates credential checking to AD, then swaps in platform authorities.
     * Avoids leaking AD group noise into our ROLE_* contract.
     */
    static final class AdBackedAuthenticationProvider implements AuthenticationProvider {

        private final ActiveDirectoryLdapAuthenticationProvider delegate;
        private final UserDetailsService userDetailsService;
        private final UserAccountService accounts;

        AdBackedAuthenticationProvider(ActiveDirectoryLdapAuthenticationProvider delegate,
                                       UserDetailsService userDetailsService,
                                       UserAccountService accounts) {
            this.delegate = delegate;
            this.userDetailsService = userDetailsService;
            this.accounts = accounts;
        }

        @Override
        public Authentication authenticate(Authentication authentication) throws AuthenticationException {
            String username = String.valueOf(authentication.getPrincipal());
            String ip = authentication.getDetails() instanceof WebAuthenticationDetails d
                    ? d.getRemoteAddress() : "unknown";

            Authentication adResult;
            try {
                adResult = delegate.authenticate(authentication);
            } catch (BadCredentialsException e) {
                accounts.onLoginFailure("LDAP", username, null, "AD bad credentials", ip, "n/a");
                throw new BadCredentialsException("Invalid username or password");
            }

            // sync the local shadow account and load platform roles
            UserDetails user;
            try {
                user = userDetailsService.loadUserByUsername(username);
            } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
                accounts.onLoginFailure("LDAP", username, null, "no local shadow account", ip, "n/a");
                throw new BadCredentialsException(
                        "AD account is not provisioned in the platform: " + username);
            }
            if (!user.isEnabled()) {
                accounts.onLoginFailure("LDAP", username,
                        user instanceof io.github.shiyi.auth.server.auth.PlatformUser p ? p.id() : null,
                        "account disabled", ip, "n/a");
                throw new DisabledException("Account disabled");
            }
            if (!user.isAccountNonLocked()) {
                throw new LockedException("Account locked");
            }

            accounts.onLoginSuccess("LDAP", username,
                    user instanceof io.github.shiyi.auth.server.auth.PlatformUser p ? p.id() : null,
                    ip, "n/a");

            return UsernamePasswordAuthenticationToken.authenticated(
                    user, adResult.getCredentials(),
                    io.github.shiyi.auth.server.auth.AuthenticationFactors.of(
                            user, io.github.shiyi.auth.server.auth.AuthenticationFactors.PASSWORD));
        }

        @Override
        public boolean supports(Class<?> authentication) {
            return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
        }
    }
}
