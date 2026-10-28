package io.github.shiyi.auth.user.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create-user request.
 *
 * @param password initial password; subject to the password policy
 *                 (>= 8 chars, at least 3 of: lower / upper / digit / special)
 */
public record UserCreateRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[a-zA-Z0-9_.-]+$",
                message = "username allows letters, digits, '_', '.', '-' only")
        String username,

        @NotBlank @Size(min = 8, max = 64) String password,

        @NotBlank @Size(max = 64) String realName,

        @Size(max = 64) String employeeNo,

        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "invalid CN mobile number")
        String mobile,

        @jakarta.validation.constraints.Email String email,

        Long orgId,

        List<Long> roleIds) {
}
