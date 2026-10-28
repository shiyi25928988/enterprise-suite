package io.github.shiyi.auth.user.web;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.shiyi.auth.common.api.ApiResult;
import io.github.shiyi.auth.user.dto.RoleRequest;
import io.github.shiyi.auth.user.entity.SysRole;
import io.github.shiyi.auth.user.service.RoleService;

import java.util.List;
import java.util.Map;

/**
 * Role management API (development doc 8.4). Permission-point binding
 * (sys_role_permission) lands with the admin UI milestone.
 */
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<List<SysRole>> list() {
        return ApiResult.ok(roleService.list());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<SysRole> create(@Valid @RequestBody RoleRequest request) {
        return ApiResult.ok(roleService.create(request));
    }

    /** body: {"roleName": "...", "dataScope": 1, "status": 1} — all optional */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<SysRole> update(@PathVariable Long id,
                                     @RequestBody Map<String, Object> body) {
        return ApiResult.ok(roleService.update(id,
                (String) body.get("roleName"),
                body.get("dataScope") == null ? null : ((Number) body.get("dataScope")).intValue(),
                body.get("status") == null ? null : ((Number) body.get("status")).intValue()));
    }
}
