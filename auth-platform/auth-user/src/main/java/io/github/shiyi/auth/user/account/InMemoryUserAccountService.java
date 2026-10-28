package io.github.shiyi.auth.user.account;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * In-memory fallback (no DataSource). Same seed accounts as the skeleton stage.
 */
class InMemoryUserAccountService implements UserAccountService {

    private final Map<String, UserAccount> users = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> roles = new ConcurrentHashMap<>();

    InMemoryUserAccountService() {
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        users.put("admin", new UserAccount(1L, "admin",
                encoder.encode("admin123"), "Administrator", 1));
        users.put("zhangsan", new UserAccount(2L, "zhangsan",
                encoder.encode("Zhang@123"), "Zhang San", 1));
        roles.put(1L, List.of("ADMIN"));
        roles.put(2L, List.of("USER"));
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return Optional.ofNullable(users.get(username));
    }

    @Override
    public List<String> findRoleCodes(Long userId) {
        return roles.getOrDefault(userId, List.of());
    }

    @Override
    public void onLoginSuccess(String username, Long userId, String ip, String userAgent) {
        // no-op in memory mode
    }

    @Override
    public boolean onLoginFailure(String username, Long userId, String failReason,
                                  String ip, String userAgent) {
        return false;
    }
}
