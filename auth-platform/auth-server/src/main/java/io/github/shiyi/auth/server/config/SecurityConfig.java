package io.github.shiyi.auth.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurer.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * Security filter chains, in strict order:
 *
 * <ol>
 *   <li>{@code @Order(1)} SAS protocol endpoints (/oauth2/*, /.well-known/*, /userinfo, /connect/*)</li>
 *   <li>{@code @Order(2)} business API (/api/**) — session-based, JSON responses</li>
 *   <li>{@code @Order(3)} login / consent pages (default generated pages until the login SPA lands)</li>
 * </ol>
 *
 * <p>WARNING: chain 1 must be restricted by {@code getEndpointsMatcher()},
 * otherwise it hijacks business endpoints.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();

        http.securityMatcher(authorizationServer.getEndpointsMatcher())
            .with(authorizationServer, configurer -> configurer.oidc(Customizer.withDefaults()))
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            // Unauthenticated browser requests are redirected to the login page;
            // non-browser (API) requests get 401.
            .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .sessionManagement(session -> session
                    .sessionCreationPolicy(
                            org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/login", "/error", "/assets/**").permitAll()
                .anyRequest().authenticated())
            // Default generated login page; replaced by the auth-ui SPA in M4.
            .formLogin(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Skeleton-stage user source (in-memory). Replaced by
     * {@code PlatformUserDetailsService} reading sys_user via auth-user in M2.
     */
    @Bean
    UserDetailsService userDetailsService(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.builder()
                    .username("admin")
                    .password(encoder.encode("admin123"))
                    .roles("ADMIN")
                    .build());
    }
}
