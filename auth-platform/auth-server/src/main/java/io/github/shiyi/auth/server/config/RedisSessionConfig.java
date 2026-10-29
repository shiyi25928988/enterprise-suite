package io.github.shiyi.auth.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;

/**
 * M6: Redis-backed HTTP session cluster storage.
 *
 * <p>Activated only under the {@code redis} profile so that demo environments
 * without a Redis instance keep the plain servlet-container (in-memory) session.
 * With it enabled, the whole {@code SecurityContext} — including the JSON login
 * session and the OIDC authorization-request/consent state — lives in Redis,
 * so any instance behind a load balancer can serve any request, and rolling
 * restarts no longer log users out.
 *
 * <p>Uses the <em>indexed</em> variant on purpose: {@code @EnableRedisHttpSession}
 * registers the plain {@code RedisSessionRepository}, which cannot be queried by
 * principal name — the online-session console ("who is signed in, kick them")
 * needs {@link org.springframework.session.FindByIndexNameSessionRepository}.</p>
 *
 * <p>Session timeout (30m) is set via {@code server.servlet.session.timeout} in
 * application.yml; {@code maxInactiveIntervalInSeconds} here is the fallback.</p>
 */
@Configuration
@Profile("redis")
@EnableRedisIndexedHttpSession(maxInactiveIntervalInSeconds = 1800)
public class RedisSessionConfig {
}
