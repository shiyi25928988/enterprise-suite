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

    SysRole create(RoleRequest request);

    SysRole update(Long id, String roleName, Integer dataScope, Integer status);
}
