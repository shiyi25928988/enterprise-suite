package io.github.shiyi.auth.user.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Full user profile for the admin detail view — everything the list DTO hides
 * (contact fields, source, MFA/password state, timestamps, role names).
 *
 * @param orgName              resolved org name, null when unassigned
 * @param roles                assigned roles with id/code/name
 * @param source               1 local 2 LDAP 3 WeCom 4 DingTalk
 * @param mustChangePassword   1 = initial password not replaced yet
 */
public record UserDetailDTO(
        Long id,
        String username,
        String realName,
        String employeeNo,
        String mobile,
        String email,
        Long orgId,
        String orgName,
        String orgPath,
        Integer source,
        Integer status,
        Integer mfaEnabled,
        Integer mustChangePassword,
        LocalDateTime lastLoginAt,
        LocalDateTime passwordUpdatedAt,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        List<RoleRef> roles) {

    /** Role summary line: {id, code, name} for display without a second request. */
    public record RoleRef(Long id, String code, String name) {
    }
}
