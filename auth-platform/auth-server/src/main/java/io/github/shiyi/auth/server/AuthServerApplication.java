package io.github.shiyi.auth.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the unified authentication platform.
 *
 * <p>Aggregates: Spring Authorization Server 7 (protocol layer)
 * + auth-user (user / organization / role domain).</p>
 */
@SpringBootApplication
public class AuthServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
