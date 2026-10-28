package io.github.shiyi.auth.user.web;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.shiyi.auth.common.api.ApiResult;
import io.github.shiyi.auth.common.api.PageResult;
import io.github.shiyi.auth.user.dto.UserCreateRequest;
import io.github.shiyi.auth.user.dto.UserDTO;
import io.github.shiyi.auth.user.dto.UserUpdateRequest;
import io.github.shiyi.auth.user.service.UserService;

/**
 * User management API (development doc 8.3). Mutations require ROLE_ADMIN;
 * method-level security via @EnableMethodSecurity.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResult<PageResult<UserDTO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) Integer status) {
        return ApiResult.ok(userService.page(page, size, keyword, orgId, status));
    }

    @GetMapping("/{id}")
    public ApiResult<UserDTO> detail(@PathVariable Long id) {
        return ApiResult.ok(userService.findById(id)
                .orElseThrow(() -> new io.github.shiyi.auth.common.exception.BizException(
                        io.github.shiyi.auth.common.api.ErrorCode.NOT_FOUND)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<UserDTO> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResult.ok(userService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<UserDTO> update(@PathVariable Long id,
                                     @Valid @RequestBody UserUpdateRequest request) {
        return ApiResult.ok(userService.update(id, request));
    }

    /** body: {"status": 0|1|2} */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<Void> changeStatus(@PathVariable Long id,
                                        @RequestBody Map<String, Integer> body) {
        userService.changeStatus(id, body.get("status"));
        return ApiResult.ok();
    }

    /** body: {"roleIds": [1, 2]} — replaces the whole set */
    @PutMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<Void> assignRoles(@PathVariable Long id,
                                       @RequestBody Map<String, List<Long>> body) {
        userService.assignRoles(id, body.get("roleIds"));
        return ApiResult.ok();
    }

    /** body: {"newPassword": "..."} — policy-checked, encoded server-side */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<Void> resetPassword(@PathVariable Long id,
                                         @RequestBody Map<String, String> body) {
        userService.resetPassword(id, body.get("newPassword"));
        return ApiResult.ok();
    }
}
