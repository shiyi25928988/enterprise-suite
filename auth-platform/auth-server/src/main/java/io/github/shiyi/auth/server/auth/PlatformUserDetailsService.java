package io.github.shiyi.auth.server.auth;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import io.github.shiyi.auth.user.account.UserAccount;
import io.github.shiyi.auth.user.account.UserAccountService;

/**
 * Loads login accounts from the user domain ({@code sys_user} via
 * {@link UserAccountService}, dual-mode JDBC/in-memory).
 *
 * <p>Account status mapping: 1 → enabled, 0 → disabled, 2 → locked
 * (auto-locked after 5 consecutive failures — see JdbcUserAccountService).</p>
 */
@Service
public class PlatformUserDetailsService implements UserDetailsService {

    private final UserAccountService accounts;

    public PlatformUserDetailsService(UserAccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount account = accounts.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("user not found: " + username));

        boolean enabled = account.status() != null && account.status() == 1;
        boolean locked = account.status() != null && account.status() == 2;

        List<GrantedAuthority> authorities = accounts.findRoleCodes(account.id()).stream()
                .map(code -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + code))
                .toList();

        return new PlatformUser(
                account.id(),
                account.username(),
                account.password(),
                account.realName(),
                !locked,
                enabled,
                account.mfaEnabled(),
                authorities);
    }
}
