package io.github.shiyi.auth.server.app;

import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Application registry wiring.
 *
 * <p>The registry is JDBC-only: it writes {@code sys_app} rows and must keep the
 * {@code oauth2_registered_client} table in sync, which does not exist in the
 * in-memory demo mode. The admin API therefore reports a clear error there, and
 * the access filter simply steps aside.</p>
 */
@Configuration
public class AppConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.datasource.url")
    AppRegistryService appRegistryService(JdbcTemplate jdbc, RegisteredClientRepository clients,
                                          PasswordEncoder passwordEncoder) {
        return new AppRegistryService(jdbc, clients, passwordEncoder);
    }

    /** Adopts protocol clients that were seeded/created without a registry row. */
    @Bean
    @ConditionalOnProperty(name = "spring.datasource.url")
    ApplicationRunner appRegistrySeeder(AppRegistryService registry) {
        return args -> registry.seedMissingClients();
    }

    @Bean
    AppAccessFilter appAccessFilter(ObjectProvider<AppRegistryService> registry,
                                    ObjectMapper objectMapper,
                                    @Value("${auth.app.forbidden-redirect:/403}") String forbiddenRedirect) {
        return new AppAccessFilter(registry, objectMapper, forbiddenRedirect);
    }
}
