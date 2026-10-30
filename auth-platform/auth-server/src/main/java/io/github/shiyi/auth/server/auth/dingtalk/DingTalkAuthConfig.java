package io.github.shiyi.auth.server.auth.dingtalk;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;

import io.github.shiyi.auth.user.account.UserAccountService;

/**
 * DingTalk scan-login wiring (M5c) — mirrors {@code WeComAuthConfig}: the
 * properties bean always exists (config endpoint answers "disabled" cleanly),
 * the provider only when auth.dingtalk.enabled=true.
 */
@Configuration
@EnableConfigurationProperties(DingTalkProperties.class)
public class DingTalkAuthConfig {

    @Bean
    DingTalkClient dingTalkClient(DingTalkProperties props) {
        return new DingTalkClient(props);
    }

    @Configuration
    @ConditionalOnProperty(prefix = "auth.dingtalk", name = "enabled", havingValue = "true")
    static class DingTalkProviderConfig {

        @Bean
        AuthenticationProvider dingTalkAuthenticationProvider(DingTalkClient dingtalk,
                                                              UserDetailsService userDetailsService,
                                                              UserAccountService accounts) {
            return new DingTalkAuthenticationProvider(dingtalk, userDetailsService, accounts);
        }
    }
}
