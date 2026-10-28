package io.github.shiyi.auth.user.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.github.shiyi.auth.common.api.ErrorCode;
import io.github.shiyi.auth.common.api.PageResult;
import io.github.shiyi.auth.common.exception.BizException;
import io.github.shiyi.auth.user.dto.UserCreateRequest;
import io.github.shiyi.auth.user.dto.UserDTO;
import io.github.shiyi.auth.user.dto.UserUpdateRequest;

/**
 * In-memory fallback (no DataSource): reads work, mutations are rejected —
 * use the JDBC profile for real testing.
 */
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

    private static BizException readOnly() {
        return new BizException(ErrorCode.BAD_REQUEST,
                "in-memory mode is read-only; start with the jdbc profile to mutate");
    }

    @Override
    public Optional<UserDTO> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<UserDTO> findAll() {
        return List.copyOf(store.values());
    }

    @Override
    public PageResult<UserDTO> page(int page, int size, String keyword, Long orgId, Integer status) {
        List<UserDTO> filtered = store.values().stream()
                .filter(u -> keyword == null || keyword.isBlank()
                        || u.username().contains(keyword) || u.realName().contains(keyword))
                .filter(u -> status == null || status.equals(u.status()))
                .sorted(java.util.Comparator.comparing(UserDTO::id).reversed())
                .toList();
        int from = Math.min((page - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        return new PageResult<>(filtered.subList(from, to), filtered.size(), page, size);
    }

    @Override
    public UserDTO create(UserCreateRequest request) {
        throw readOnly();
    }

    @Override
    public UserDTO update(Long id, UserUpdateRequest request) {
        throw readOnly();
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        throw readOnly();
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        throw readOnly();
    }

    @Override
    public void assignRoles(Long id, List<Long> roleIds) {
        throw readOnly();
    }
}
