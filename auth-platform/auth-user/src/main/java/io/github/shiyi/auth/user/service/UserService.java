package io.github.shiyi.auth.user.service;

import java.util.List;
import java.util.Optional;

import io.github.shiyi.auth.common.api.PageResult;
import io.github.shiyi.auth.user.dto.UserCreateRequest;
import io.github.shiyi.auth.user.dto.UserDTO;
import io.github.shiyi.auth.user.dto.UserUpdateRequest;

/**
 * User domain service (dual mode: JDBC / in-memory — see UserDomainConfig).
 */
public interface UserService {

    Optional<UserDTO> findById(Long id);

    List<UserDTO> findAll();

    /**
     * Paged query.
     *
     * @param keyword    matches username / realName (fuzzy), nullable
     * @param orgId      filter by organization subtree (org_path LIKE), nullable
     * @param status     1 enabled 0 disabled 2 locked, null = all
     */
    PageResult<UserDTO> page(int page, int size, String keyword, Long orgId, Integer status);

    /** Full profile for the admin detail view (contact fields, org name, role names). */
    io.github.shiyi.auth.user.dto.UserDetailDTO detail(Long id);

    /**
     * Moves the user to another organization. {@code orgId = null} clears the
     * assignment; a non-null id must reference an existing org.
     */
    void moveOrg(Long id, Long orgId);

    UserDTO create(UserCreateRequest request);

    UserDTO update(Long id, UserUpdateRequest request);

    /** status: 1 enabled 0 disabled 2 locked */
    void changeStatus(Long id, Integer status);

    void resetPassword(Long id, String newPassword);

    /** Replaces the whole role set of the user. */
    void assignRoles(Long id, List<Long> roleIds);
}
