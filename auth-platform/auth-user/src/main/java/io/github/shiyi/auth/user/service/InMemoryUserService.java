package io.github.shiyi.auth.user.service;

import io.github.shiyi.auth.user.dto.UserDTO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory stub implementation. Replaced by MyBatis-Plus JDBC implementation in M3.
 */
@Service
public class InMemoryUserService implements UserService {

    private final Map<Long, UserDTO> store = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(0);

    public InMemoryUserService() {
        create("admin", "Administrator", 1L);
        create("zhangsan", "Zhang San", 2L);
    }

    private void create(String username, String realName, Long orgId) {
        long id = idSeq.incrementAndGet();
        store.put(id, new UserDTO(id, username, realName, orgId, 1, null));
    }

    @Override
    public Optional<UserDTO> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<UserDTO> findAll() {
        return List.copyOf(store.values());
    }
}
