package io.github.shiyi.auth.user.service;

import java.util.List;

import io.github.shiyi.auth.user.dto.RoleRequest;
import io.github.shiyi.auth.user.entity.SysRole;

/**
 * Role service (dual mode). Permission-point binding (sys_role_permission)
 * lands with the admin UI milestone.
 */
public interface RoleService {

    List<SysRole> list();

    /**
     * Reference statistics for every role in one round trip — used by the role
     * list (引用数) and the delete confirmation.
     */
    List<io.github.shiyi.auth.user.dto.RoleUsageDTO> usage();

    SysRole create(RoleRequest request);

    SysRole update(Long id, String roleName, Integer dataScope, Integer status);

    /**
     * Deletes a role. Refuses: the built-in ADMIN role, roles still held by
     * users, roles still granted to applications (all 409 CONFLICT).
     */
    void delete(Long id);
}
