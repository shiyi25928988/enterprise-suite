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
    SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http, io.github.shiyi.auth.server.app.AppAccessFilter appAccessFilter,
            tools.jackson.databind.ObjectMapper objectMapper)
            throws Exception {
        // Security 7: no static factory any more, the configurer is instantiated directly.
        OAuth2AuthorizationServerConfigurer authorizationServer = new OAuth2AuthorizationServerConfigurer();

        http.securityMatcher(authorizationServer.getEndpointsMatcher())
            .with(authorizationServer, configurer -> configurer.oidc(Customizer.withDefaults()))
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            // Role → application gate: must run once the security context is loaded
            // (authentication is already resolved on the returning authorize call)
            // but before the protocol endpoints issue codes.
            .addFilterAfter(appAccessFilter, org.springframework.security.web.context.SecurityContextHolderFilter.class)
            // An account still on its initial password may not mint codes for any
            // client — otherwise "must change password" would be UI-only advice.
            .addFilterAfter(new io.github.shiyi.auth.server.auth.MustChangePasswordFilter(objectMapper),
                    org.springframework.security.web.context.SecurityContextHolderFilter.class)
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
                                               CsrfTokenRepository csrfTokenRepository,
                                               io.github.shiyi.auth.user.account.UserAccountService accounts)
            throws Exception {
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
            .addFilterAfter(new io.github.shiyi.auth.server.auth.MustChangePasswordFilter(objectMapper),
                    org.springframework.security.web.context.SecurityContextHolderFilter.class)
            .addFilterAt(jsonLoginFilter(objectMapper, authenticationManager, accounts),
                    UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** ③ SPA shell: /login, /403 and static assets are public; everything else needs a session. */
    @Bean
    @Order(3)
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/login", "/index.html", "/error", "/assets/**", "/403",
                        "/callback", "/wecom-callback").permitAll()
                // probe endpoints: liveness/readiness must not require a session;
                // prometheus must be reachable by the scraper — restrict via network
                // policy in production, the payload contains request-URI metrics
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info",
                        "/actuator/prometheus").permitAll()
                .anyRequest().authenticated())
            // The login screen is the SPA's own page (it posts JSON to /api/auth/login),
            // so the generated form-login page is switched off; unauthenticated
            // navigations are redirected to /login instead.
            .formLogin(form -> form.disable())
            .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    private JsonUsernamePasswordFilter jsonLoginFilter(ObjectMapper objectMapper,
                                                       AuthenticationManager authenticationManager,
                                                       io.github.shiyi.auth.user.account.UserAccountService accounts) {
        return new JsonUsernamePasswordFilter(
                authenticationManager,
                objectMapper,
                new LoginSuccessHandler(objectMapper, accounts),
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
     *
     * <p>The event publisher is wired explicitly so authentication events reach
     * {@code AuthMetrics} — without it ProviderManager stays silent.</p>
     */
    @Bean
    AuthenticationManager authenticationManager(PlatformUserDetailsService userDetailsService,
                                                PasswordEncoder passwordEncoder,
                                                io.github.shiyi.auth.user.account.UserAccountService accounts,
                                                io.github.shiyi.auth.server.auth.mfa.MfaChallengeService mfaChallenges,
                                                org.springframework.security.authentication.AuthenticationEventPublisher eventPublisher,
                                                org.springframework.beans.factory.ObjectProvider<org.springframework.security.authentication.AuthenticationProvider> extraProviders) {
        var providers = new java.util.ArrayList<org.springframework.security.authentication.AuthenticationProvider>();
        providers.add(new PlatformAuthenticationProvider(
                userDetailsService, passwordEncoder, accounts, mfaChallenges));
        extraProviders.orderedStream().forEach(providers::add);
        // Security 7: publisher is injected via setter, not a constructor argument
        var manager = new ProviderManager(providers);
        manager.setAuthenticationEventPublisher(eventPublisher);
        return manager;
    }
}
