package io.github.shiyi.auth.user.dto;

/**
 * Per-role reference statistics for the role list / delete confirmation.
 *
 * @param roleId    sys_role.id
 * @param userCount how many accounts hold the role (sys_user_role)
 * @param appCount  how many applications grant the role (sys_app_role)
 * @param builtIn   true for system roles (ADMIN) that must never be deleted
 */
public record RoleUsageDTO(Long roleId, long userCount, long appCount, boolean builtIn) {
}
