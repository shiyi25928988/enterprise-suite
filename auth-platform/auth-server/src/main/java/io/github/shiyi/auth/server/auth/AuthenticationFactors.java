package io.github.shiyi.auth.server.auth;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security 7 / SAS 7 require authenticating an <em>authentication factor</em>
 * in addition to roles.
 *
 * <p>SAS 7's {@code JwtGenerator} derives the {@code auth_time} claim from
 * {@link FactorGrantedAuthority#getIssuedAt()} and fails the token request with
 * {@code IllegalArgumentException: authenticationTime cannot be null} when no
 * factor authority is present. Every successful authentication must therefore
 * carry at least one factor.</p>
 */
public final class AuthenticationFactors {

    /** Plain factor names; {@code withFactor} prefixes them with {@code FACTOR_}. */
    public static final String PASSWORD = "PASSWORD";
    public static final String TOTP = "TOTP";
    public static final String OTT = "OTT";

    private AuthenticationFactors() {
    }

    /**
     * Authorities of the user plus the given factors, all stamped with the same
     * issue time (the moment the factors were verified).
     *
     * <p>Pass the plain factor name ({@code PASSWORD}, {@code TOTP}, ...):
     * {@code FactorGrantedAuthority.withFactor} adds the {@code FACTOR_} prefix
     * itself and rejects values that already carry it.</p>
     */
    public static List<GrantedAuthority> of(UserDetails user, String... factors) {
        List<GrantedAuthority> authorities = new ArrayList<>(user.getAuthorities());
        Instant issuedAt = Instant.now();
        for (String factor : factors) {
            authorities.add(FactorGrantedAuthority.withFactor(factor).issuedAt(issuedAt).build());
        }
        return authorities;
    }
}
