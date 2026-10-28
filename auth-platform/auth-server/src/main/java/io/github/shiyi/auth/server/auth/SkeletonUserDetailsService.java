package io.github.shiyi.auth.server.auth;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * SKELETON-STAGE user source backed by an in-memory map.
 *
 * <p>M2/M3 will replace the store with sys_user lookups via auth-user
 * (plus LDAP / WeCom sources behind the same interface).</p>
 */
@Service
public class SkeletonUserDetailsService implements UserDetailsService {

    private final Map<String, PlatformUser> users = new ConcurrentHashMap<>();

    public SkeletonUserDetailsService(PasswordEncoder encoder) {
        register("admin", "admin123", "Administrator", "ROLE_ADMIN", encoder);
        register("zhangsan", "Zhang@123", "Zhang San", "ROLE_USER", encoder);
    }

    private void register(String username, String rawPassword, String realName,
                          String role, PasswordEncoder encoder) {
        users.put(username, new PlatformUser(
                (long) (users.size() + 1),
                username,
                encoder.encode(rawPassword),
                realName,
                true,
                true,
                List.of(new SimpleGrantedAuthority(role))));
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        PlatformUser user = users.get(username);
        if (user == null) {
            throw new UsernameNotFoundException("user not found: " + username);
        }
        return user;
    }
}
