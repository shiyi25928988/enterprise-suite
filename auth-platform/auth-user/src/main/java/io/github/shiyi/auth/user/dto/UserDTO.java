package io.github.shiyi.auth.user.dto;

import java.time.LocalDateTime;

/**
 * User view object returned by the user API.
 *
 * @param id           internal id
 * @param username     login name / employee number
 * @param realName     display name
 * @param orgId        organization id
 * @param status       1 enabled, 0 disabled, 2 locked
 * @param lastLoginAt  last successful login time
 */
public record UserDTO(
        Long id,
        String username,
        String realName,
        Long orgId,
        Integer status,
        LocalDateTime lastLoginAt) {
}
