package io.github.shiyi.auth.server.config;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import io.github.shiyi.auth.common.api.ApiResult;
import io.github.shiyi.auth.common.api.ErrorCode;
import io.github.shiyi.auth.server.auth.JsonResponses;
import io.github.shiyi.auth.server.auth.JsonUsernamePasswordFilter;
import io.github.shiyi.auth.server.auth.LoginFailureHandler;
import io.github.shiyi.auth.server.auth.LoginSuccessHandler;
import io.github.shiyi.auth.server.auth.PlatformAuthenticationProvider;
import io.github.shiyi.auth.server.auth.PlatformUserDetailsService;
import io.github.shiyi.auth.server.auth.SpaCsrfTokenRequestHandler;

/**
 * Security filter chains, in strict order:
 *
 * <ol>
 *   <li>{@code @Order(1)} SAS protocol endpoints (/oauth2/*, /.well-known/*, /userinfo, /connect/*)</li>
 *   <li>{@code @Order(2)} business API (/api/**) — session-based, CSRF cookie mode, JSON errors</li>
 *   <li>{@code @Order(3)} login / consent pages (default generated pages until the login SPA lands)</li>
 * </ol>
 *
 * <p>WARNING: chain 1 must be restricted by {@code getEndpointsMatcher()},
 * otherwise it hijacks business endpoints.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** ① SAS protocol endpoints. */
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        // Security 7: no static factory any more, the configurer is instantiated directly.
        OAuth2AuthorizationServerConfigurer authorizationServer = new OAuth2AuthorizationServerConfigurer();

        http.securityMatcher(authorizationServer.getEndpointsMatcher())
            .with(authorizationServer, configurer -> configurer.oidc(Customizer.withDefaults()))
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            // Unauthenticated browser requests redirect to /login; API clients get 401.
            .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    /** ② Business API (/api/**): session auth + SPA-style CSRF + JSON 401 + JSON logout. */
    @Bean
    @Order(2)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                               ObjectMapper objectMapper,
                                               AuthenticationManager authenticationManager,
                                               CsrfTokenRepository csrfTokenRepository) throws Exception {
        http.securityMatcher("/api/**")
            .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers("/api/auth/csrf", "/api/auth/login",
                            "/api/auth/wecom/config", "/api/auth/wecom/login",
                            "/api/auth/mfa/verify").permitAll()
                    .anyRequest().authenticated())
            .csrf(csrf -> csrf
                    .csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
            .exceptionHandling(e -> e.authenticationEntryPoint((request, response, authException) ->
                    JsonResponses.write(response, objectMapper, HttpStatus.UNAUTHORIZED,
                            ApiResult.fail(ErrorCode.UNAUTHENTICATED))))
            .logout(logout -> logout
                    .logoutUrl("/api/auth/logout")
                    .logoutSuccessHandler(jsonLogoutSuccessHandler(objectMapper)))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .addFilterAt(jsonLoginFilter(objectMapper, authenticationManager),
                    UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** ③ Login / consent pages: default generated pages until the auth-ui SPA lands (M4). */
    @Bean
    @Order(3)
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/login", "/error", "/assets/**").permitAll()
                // probe endpoints: liveness/readiness must not require a session
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .anyRequest().authenticated())
            .formLogin(Customizer.withDefaults());
        return http.build();
    }

    private JsonUsernamePasswordFilter jsonLoginFilter(ObjectMapper objectMapper,
                                                       AuthenticationManager authenticationManager) {
        return new JsonUsernamePasswordFilter(
                authenticationManager,
                objectMapper,
                new LoginSuccessHandler(objectMapper),
                new LoginFailureHandler(objectMapper));
    }

    private LogoutSuccessHandler jsonLogoutSuccessHandler(ObjectMapper objectMapper) {
        return (HttpServletRequest request, HttpServletResponse response,
                org.springframework.security.core.Authentication authentication) -> {
            request.getSession().invalidate();
            JsonResponses.write(response, objectMapper, HttpStatus.OK, ApiResult.ok());
        };
    }

    /** Shared with AuthController so the JSON payload and the cookie carry the same value. */
    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Provider registry: the password provider is always present; conditional
     * M5 providers (LDAP/AD, WeCom QR) are picked up from the context when
     * their properties enable them (auth.ldap.enabled / auth.wecom.enabled).
     */
    @Bean
    AuthenticationManager authenticationManager(PlatformUserDetailsService userDetailsService,
                                                PasswordEncoder passwordEncoder,
                                                io.github.shiyi.auth.user.account.UserAccountService accounts,
                                                io.github.shiyi.auth.server.auth.mfa.MfaChallengeService mfaChallenges,
                                                org.springframework.beans.factory.ObjectProvider<org.springframework.security.authentication.AuthenticationProvider> extraProviders) {
        var providers = new java.util.ArrayList<org.springframework.security.authentication.AuthenticationProvider>();
        providers.add(new PlatformAuthenticationProvider(
                userDetailsService, passwordEncoder, accounts, mfaChallenges));
        extraProviders.orderedStream().forEach(providers::add);
        return new ProviderManager(providers);
    }
}
