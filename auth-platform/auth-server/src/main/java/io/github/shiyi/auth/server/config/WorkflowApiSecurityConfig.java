package io.github.shiyi.auth.server.config;

import tools.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import io.github.shiyi.auth.server.auth.JsonResponses;
import io.github.shiyi.auth.server.auth.MustChangePasswordFilter;
import io.github.shiyi.auth.server.auth.SpaCsrfTokenRequestHandler;

/**
 * Security chain dedicated to the workflow REST surface ({@code /api/v1/flow/**}).
 *
 * <p>Why a separate chain instead of letting these paths ride the generic
 * {@code /api/**} chain: the workflow endpoints have their own authorization
 * table (half of them are ADMIN-only) and, more importantly, they are the
 * <em>seam</em> of the planned service split. When workflow becomes its own
 * deployment, only the marked block below changes — everything above it is the
 * contract the callers already rely on.</p>
 *
 * <h2>Role contract</h2>
 * <table border="1">
 *   <caption>authorization table</caption>
 *   <tr><th>Endpoint</th><th>Who</th></tr>
 *   <tr><td>POST /deploy</td><td>ADMIN</td></tr>
 *   <tr><td>GET /definitions</td><td>ADMIN</td></tr>
 *   <tr><td>GET /forms</td><td>ADMIN</td></tr>
 *   <tr><td>PUT /forms/{processKey}</td><td>ADMIN</td></tr>
 *   <tr><td>GET /analytics/**</td><td>ADMIN</td></tr>
 *   <tr><td>everything else (start, todo, done, tasks/*, forms/{key}, timeline)</td>
 *       <td>any authenticated employee</td></tr>
 * </table>
 *
 * <p>These rules are expressed with {@code hasRole} rather than by path prefix
 * alone so the table is readable in one screen. {@code @PreAuthorize} on
 * {@code FlowController} stays as the second line of defence: should the chain
 * ever be bypassed (a stray {@code securityMatcher}, a mock in a test), the
 * method-level check still fails closed.</p>
 *
 * <p>This chain must be ordered <strong>before</strong> the {@code /api/**}
 * chain. If the order flips, the generic chain matches first and every one of
 * these rules silently disappears (all endpoints stay "authenticated only") —
 * {@code WorkflowApiSecurityTest} pins that down.</p>
 */
@Configuration
public class WorkflowApiSecurityConfig {

    /** Path prefix owned by the workflow module. */
    public static final String FLOW_BASE = "/api/v1/flow";

    @Bean
    @Order(2)
    SecurityFilterChain workflowApiSecurityFilterChain(HttpSecurity http,
                                                       ObjectMapper objectMapper,
                                                       CsrfTokenRepository csrfTokenRepository)
            throws Exception {
        http.securityMatcher(FLOW_BASE + "/**")
            .authorizeHttpRequests(authorize -> authorize
                    // ---- ADMIN only: design-time and cross-tenant reporting ----
                    .requestMatchers(HttpMethod.POST, FLOW_BASE + "/deploy").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, FLOW_BASE + "/definitions").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, FLOW_BASE + "/forms").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, FLOW_BASE + "/forms/*").hasRole("ADMIN")
                    .requestMatchers(FLOW_BASE + "/analytics/**").hasRole("ADMIN")
                    // ---- everyone signed in: my own work ----
                    .anyRequest().authenticated())
            // ============================================================
            // SPLIT SEAM — everything from here to the end of the method is
            // the "I am the same process as auth-server" assumption:
            //   * the session cookie and the shared Redis session store,
            //   * the double-submit CSRF cookie the SPA already sends,
            //   * the login/logout endpoints living in the /api/** chain,
            //   * the initial-password gate reading a session attribute.
            // A standalone workflow service replaces this block with
            //   .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
            // plus a JwtDecoder pointing at auth-server's JWKS, drops CSRF
            // (no cookies any more) and drops the MustChangePasswordFilter
            // (the token is only issued after the password is changed).
            // The authorizeHttpRequests block above does not change: principal
            // name and ROLE_* authorities are the same in both worlds.
            // ============================================================
            .csrf(csrf -> csrf
                    .csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .exceptionHandling(e -> e
                    .authenticationEntryPoint(JsonResponses.unauthenticated(objectMapper))
                    .accessDeniedHandler(JsonResponses.forbidden(objectMapper)))
            .addFilterAfter(new MustChangePasswordFilter(objectMapper),
                    org.springframework.security.web.context.SecurityContextHolderFilter.class);
        return http.build();
    }
}
