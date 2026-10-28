package io.github.shiyi.auth.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Update-user request: profile fields only; status / password / roles have
 * dedicated endpoints.
 */
public record UserUpdateRequest(
        @NotBlank @Size(max = 64) String realName,
        @Size(max = 64) String employeeNo,
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$") String mobile,
        @Email String email,
        Long orgId) {
}
