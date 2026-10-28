package io.github.shiyi.auth.server.persistence;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

/**
 * Signing-key provider: loads active keys (kid history) or creates the first one.
 *
 * <p>Two implementations exist (see PersistenceConfig):</p>
 * <ul>
 *   <li>{@link JdbcJwkKeyService} — keys persisted in {@code auth_jwk}, surviving restarts;</li>
 *   <li>{@link EphemeralJwkKeyService} — dev/skeleton fallback, a new key per boot
 *       (invalidates previously issued tokens, dev only).</li>
 * </ul>
 *
 * <p>Production: move private key material to KMS/Vault and keep only the
 * public JWK + kid reference in the database.</p>
 */
public interface JwkKeyService {

    /** Returns the active keys ordered by creation time (newest last). */
    List<JWK> loadOrCreateActiveKeys();

    default JWKSet toJwkSet() {
        return new JWKSet(loadOrCreateActiveKeys());
    }

    static JWK generateRsaJwk() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key generator not available", e);
        }
    }
}

/** JDBC implementation: keys persist across restarts and support rotation history. */
class JdbcJwkKeyService implements JwkKeyService {

    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    JdbcJwkKeyService(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<JWK> loadOrCreateActiveKeys() {
        List<String> jsonList = jdbc.queryForList(
                "SELECT key_json FROM auth_jwk WHERE status = 1 ORDER BY created_at",
                String.class);

        List<JWK> keys = new ArrayList<>();
        for (String json : jsonList) {
            try {
                keys.add(JWK.parse(json));
            } catch (java.text.ParseException e) {
                throw new IllegalStateException("corrupted JWK row in auth_jwk", e);
            }
        }

        if (keys.isEmpty()) {
            JWK key = JwkKeyService.generateRsaJwk();
            jdbc.update("INSERT INTO auth_jwk (kid, key_json, status) VALUES (?, ?, 1)",
                    key.getKeyID(), key.toJSONString());
            keys.add(key);
        }
        return keys;
    }
}

/** Skeleton/dev fallback: ephemeral key per boot. NEVER use in production. */
class EphemeralJwkKeyService implements JwkKeyService {

    private final JWKSet keySet = new JWKSet(JwkKeyService.generateRsaJwk());

    @Override
    public List<JWK> loadOrCreateActiveKeys() {
        return List.copyOf(keySet.getKeys());
    }
}
