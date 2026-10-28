package io.github.shiyi.auth.server.auth.wecom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;

import io.github.shiyi.auth.user.account.UserAccountService;

/**
 * WeCom scan-login wiring. The properties bean is always registered (the
 * config endpoint must answer "disabled" cleanly); the authentication
 * provider only exists when auth.wecom.enabled=true.
 */
@Configuration
@EnableConfigurationProperties(WeComProperties.class)
public class WeComAuthConfig {

    @Configuration
    @ConditionalOnProperty(prefix = "auth.wecom", name = "enabled", havingValue = "true")
    static class WeComProviderConfig {

        @Bean
        AuthenticationProvider weComAuthenticationProvider(WeComClient wecom,
                                                           UserDetailsService userDetailsService,
                                                           UserAccountService accounts) {
            return new WeComAuthenticationProvider(wecom, userDetailsService, accounts);
        }
    }
}
