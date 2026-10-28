package io.github.shiyi.auth.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

/**
 * Authorization server settings.
 *
 * <p>Client repository / authorization service / JWK signing keys live in
 * {@code PersistenceConfig} (dual mode: JDBC when a DataSource is configured,
 * in-memory fallback otherwise).</p>
 */
@Configuration
public class AuthorizationServerConfig {

    @Bean
    AuthorizationServerSettings authorizationServerSettings() {
        // issuer MUST equal the externally reachable base URL.
        return AuthorizationServerSettings.builder()
                .issuer("http://localhost:9000")
                .build();
    }
}
