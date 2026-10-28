package io.github.shiyi.auth.server.auth;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Platform user exposed to Spring Security.
 *
 * <p>Kept as a dedicated type (instead of the generic Security {@code User}) so that
 * business code can safely downcast the principal to read id / realName / orgId —
 * the same shape the future JDBC-backed user source will produce.</p>
 */
public record PlatformUser(
        Long id,
        String username,
        String password,
        String realName,
        boolean accountNonLocked,
        boolean enabled,
        boolean mfaEnabled,
        List<GrantedAuthority> authorities) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
