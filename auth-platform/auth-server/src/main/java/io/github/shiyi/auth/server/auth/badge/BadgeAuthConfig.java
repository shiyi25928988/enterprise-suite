package io.github.shiyi.auth.server.auth.badge;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;

import io.github.shiyi.auth.user.account.UserAccountService;

/**
 * Badge (工牌) login wiring (M5c). Always registered — the feature costs
 * nothing until a user rotates a badge code, and unknown codes fail exactly
 * like wrong passwords (audited + anomaly-counted).
 */
@Configuration
public class BadgeAuthConfig {

    @Bean
    AuthenticationProvider badgeAuthenticationProvider(UserDetailsService userDetailsService,
                                                       UserAccountService accounts) {
        return new BadgeAuthenticationProvider(userDetailsService, accounts);
    }
}
