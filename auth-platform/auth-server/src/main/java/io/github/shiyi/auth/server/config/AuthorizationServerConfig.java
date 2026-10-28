package io.github.shiyi.auth.server.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;

/**
 * Authorization server wiring.
 *
 * <p>SKELETON STAGE: in-memory client repository and an ephemeral signing key
 * generated at startup. Both MUST be replaced before production:</p>
 * <ul>
 *   <li>{@code JdbcRegisteredClientRepository} / {@code JdbcOAuth2AuthorizationService}
 *       — M1.5 (see development doc, chapter 7.4)</li>
 *   <li>persistent {@code JWKSource} loaded from DB/KMS — an ephemeral key
 *       invalidates every issued token on restart</li>
 * </ul>
 */
@Configuration
public class AuthorizationServerConfig {

    @Bean
    JWKSource<SecurityContext> jwkSource() {
        KeyPair kp = generateRsaKey();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) kp.getPublic())
                .privateKey((RSAPrivateKey) kp.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
        return (jwkSelector, securityContext) -> jwkSelector.select(new JWKSet(rsaKey));
    }

    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        // In-process decoder: the same app validates its own JWTs without an HTTP call to JWKS.
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    RegisteredClientRepository registeredClientRepository() {
        // Demo clients; move to JDBC-backed store in M1.5.
        RegisteredClient mesWeb = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("mes-web")
                .clientAuthenticationMethod(
                        org.springframework.security.oauth2.core.ClientAuthenticationMethod.NONE)
                .authorizationGrantType(
                        org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(
                        org.springframework.security.oauth2.core.AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:8081/login/oauth2/code/mes-web")
                .scope(org.springframework.security.oauth2.core.oidc.OidcScopes.OPENID)
                .scope(org.springframework.security.oauth2.core.oidc.OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(true)
                        .requireProofKey(true)   // public client: PKCE mandatory
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(30))
                        .reuseRefreshTokens(false)   // refresh token rotation
                        .build())
                .build();

        return new InMemoryRegisteredClientRepository(mesWeb);
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings() {
        // issuer MUST equal the externally reachable base URL.
        return AuthorizationServerSettings.builder()
                .issuer("http://localhost:9000")
                .build();
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key generator not available", e);
        }
    }
}
