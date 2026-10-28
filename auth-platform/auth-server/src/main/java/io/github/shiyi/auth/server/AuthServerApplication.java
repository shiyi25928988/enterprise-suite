package io.github.shiyi.auth.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the unified authentication platform.
 *
 * <p>Aggregates: Spring Authorization Server 7 (protocol layer)
 * + auth-user (user / organization / role domain).</p>
 *
 * <p>NOTE: {@code scanBasePackages} must cover the whole {@code io.github.shiyi.auth}
 * tree. The default scan covers only this package, which silently leaves
 * auth-user's {@code UserDomainConfig} unregistered — no {@code UserAccountService}
 * bean and the context fails at the authentication provider.</p>
 */
@SpringBootApplication(scanBasePackages = "io.github.shiyi.auth")
public class AuthServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
