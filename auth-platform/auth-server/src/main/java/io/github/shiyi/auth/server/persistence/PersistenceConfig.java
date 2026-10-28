package io.github.shiyi.auth.server.persistence;

import java.time.Duration;
import java.util.UUID;

import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;

import java.time.Duration;
import java.util.UUID;

/**
 * Persistence wiring with dual mode:
 *
 * <ul>
 *   <li>a {@code DataSource} is configured (spring.datasource.url set, e.g. profile
 *       {@code jdbc}) → Flyway migrations + JDBC-backed client/authorization/consent
 *       stores + persistent JWK keys;</li>
 *   <li>no {@code DataSource} → in-memory fallback so the skeleton still boots
 *       with zero external dependencies (dev/demo only).</li>
 * </ul>
 *
 * <p>Bean conditions are evaluated top-down inside this class; keep the JDBC
 * beans declared BEFORE the in-memory fallbacks.</p>
 */
@Configuration
public class PersistenceConfig {

    private static final Logger log = LoggerFactory.getLogger(PersistenceConfig.class);

    // ---------- Registered clients ----------

    @Bean
    @ConditionalOnBean(javax.sql.DataSource.class)
    RegisteredClientRepository jdbcRegisteredClientRepository(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        return new JdbcRegisteredClientRepository(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean(RegisteredClientRepository.class)
    RegisteredClientRepository inMemoryRegisteredClientRepository() {
        log.warn("No DataSource configured - using IN-MEMORY RegisteredClientRepository (dev/demo only)");
        return new InMemoryRegisteredClientRepository(demoMesWebClient());
    }

    /** Seeds the demo client into JDBC storage on first boot (idempotent). */
    @Bean
    @ConditionalOnBean(javax.sql.DataSource.class)
    ApplicationRunner demoClientSeeder(RegisteredClientRepository repository) {
        return args -> {
            if (repository.findByClientId("mes-web") == null) {
                repository.save(demoMesWebClient());
                log.info("Seeded demo client 'mes-web' into oauth2_registered_client");
            }
        };
    }

    static RegisteredClient demoMesWebClient() {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("mes-web")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:8081/login/oauth2/code/mes-web")
                .postLogoutRedirectUri("http://localhost:8081/")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(true)
                        .requireProofKey(true)   // public client: PKCE mandatory
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(30))
                        .reuseRefreshTokens(false)   // refresh token rotation
                        .build())
                .build();
    }

    // ---------- Authorizations & consent ----------

    @Bean
    @ConditionalOnBean(javax.sql.DataSource.class)
    OAuth2AuthorizationService jdbcOAuth2AuthorizationService(
            org.springframework.jdbc.core.JdbcTemplate jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationService(jdbc, clients);
    }

    @Bean
    @ConditionalOnMissingBean(OAuth2AuthorizationService.class)
    OAuth2AuthorizationService inMemoryOAuth2AuthorizationService() {
        log.warn("No DataSource configured - using IN-MEMORY OAuth2AuthorizationService (dev/demo only)");
        return new org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService();
    }

    @Bean
    @ConditionalOnBean(javax.sql.DataSource.class)
    OAuth2AuthorizationConsentService jdbcOAuth2AuthorizationConsentService(
            org.springframework.jdbc.core.JdbcTemplate jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationConsentService(jdbc, clients);
    }

    @Bean
    @ConditionalOnMissingBean(OAuth2AuthorizationConsentService.class)
    OAuth2AuthorizationConsentService inMemoryOAuth2AuthorizationConsentService() {
        log.warn("No DataSource configured - using IN-MEMORY OAuth2AuthorizationConsentService (dev/demo only)");
        return new org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService();
    }

    // ---------- JWK signing keys ----------

    @Bean
    @ConditionalOnBean(javax.sql.DataSource.class)
    JwkKeyService jdbcJwkKeyService(JdbcTemplate jdbc) {
        log.info("JWK signing keys persisted in auth_jwk (survive restarts)");
        return new JdbcJwkKeyService(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean(JwkKeyService.class)
    JwkKeyService ephemeralJwkKeyService() {
        log.warn("No DataSource configured - EPHEMERAL JWK per boot, issued tokens invalidated on restart (dev only)");
        return new EphemeralJwkKeyService();
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(JwkKeyService keyService) {
        keyService.loadOrCreateActiveKeys(); // fail fast at boot, not at first token request
        return (jwkSelector, securityContext) -> jwkSelector.select(keyService.toJwkSet());
    }

    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        // In-process decoder: the same app validates its own JWTs without an HTTP call to JWKS.
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }
}
