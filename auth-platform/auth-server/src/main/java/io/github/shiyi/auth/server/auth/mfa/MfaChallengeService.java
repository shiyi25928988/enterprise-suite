package io.github.shiyi.auth.server.auth.mfa;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * Short-lived MFA challenges issued after a correct password.
 *
 * <p>In-memory by design for the single-node stage: 5-minute TTL, max 5 code
 * attempts, one-time use. <b>Cluster deployment must replace this with a Redis
 * implementation</b> (same as the DPoP jti cache) — otherwise a challenge
 * issued by node A is invisible to node B.</p>
 */
@Service
public class MfaChallengeService {

    static final Duration TTL = Duration.ofMinutes(5);
    static final int MAX_ATTEMPTS = 5;

    public static final class Challenge {
        private final String username;
        private final Instant expiresAt;
        private int attempts;

        Challenge(String username, Instant expiresAt) {
            this.username = username;
            this.expiresAt = expiresAt;
        }

        public String username() { return username; }
        boolean expired() { return Instant.now().isAfter(expiresAt); }
        int attempts() { return attempts; }
        void attempt() { attempts++; }
    }

    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();

    public String create(String username) {
        purgeExpired();
        String id = UUID.randomUUID().toString().replace("-", "");
        challenges.put(id, new Challenge(username, Instant.now().plus(TTL)));
        return id;
    }

    public Optional<Challenge> peek(String challengeId) {
        Challenge c = challenges.get(challengeId);
        if (c == null) {
            return Optional.empty();
        }
        if (c.expired() || c.attempts() >= MAX_ATTEMPTS) {
            challenges.remove(challengeId);
            return Optional.empty();
        }
        return Optional.of(c);
    }

    public void consume(String challengeId) {
        challenges.remove(challengeId);
    }

    public void recordFailure(String challengeId) {
        Challenge c = challenges.get(challengeId);
        if (c != null) {
            c.attempt();
            if (c.attempts() >= MAX_ATTEMPTS) {
                challenges.remove(challengeId);
            }
        }
    }

    private void purgeExpired() {
        challenges.entrySet().removeIf(e -> e.getValue().expired());
    }
}
