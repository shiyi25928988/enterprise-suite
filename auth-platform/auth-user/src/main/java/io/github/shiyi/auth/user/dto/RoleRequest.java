package io.github.shiyi.auth.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoleRequest(
        @NotBlank @Size(max = 64) @jakarta.validation.constraints.Pattern(regexp = "^[A-Z0-9_]+$",
                message = "role code allows uppercase letters, digits, '_' only")
        String roleCode,

        @NotBlank @Size(max = 64) String roleName,

        @NotNull Integer dataScope) {
}
