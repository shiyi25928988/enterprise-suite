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
    /** Absent in in-memory demo mode — batch import/export needs a real database. */
    private final org.springframework.beans.factory.ObjectProvider<io.github.shiyi.auth.user.service.UserImportExportService> batch;

    public UserController(UserService userService,
                          org.springframework.beans.factory.ObjectProvider<io.github.shiyi.auth.user.service.UserImportExportService> batch) {
        this.userService = userService;
        this.batch = batch;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<PageResult<UserDTO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) Integer status) {
        return ApiResult.ok(userService.page(page, size, keyword, orgId, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<UserDTO> detail(@PathVariable Long id) {
        return ApiResult.ok(userService.findById(id)
                .orElseThrow(() -> new io.github.shiyi.auth.common.exception.BizException(
                        io.github.shiyi.auth.common.api.ErrorCode.NOT_FOUND)));
    }

    /** Full profile: contact fields, org name/path, role names, lifecycle timestamps. */
    @GetMapping("/{id}/detail")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<io.github.shiyi.auth.user.dto.UserDetailDTO> fullDetail(@PathVariable Long id) {
        return ApiResult.ok(userService.detail(id));
    }

    /** body: {"orgId": 5 | null} — move the user to another organization. */
    @PatchMapping("/{id}/org")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<Void> moveOrg(@PathVariable Long id,
                                   @RequestBody Map<String, Object> body) {
        Object raw = body.get("orgId");
        Long orgId = raw == null ? null : ((Number) raw).longValue();
        userService.moveOrg(id, orgId);
        return ApiResult.ok();
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

    // ---------- batch import / export ----------

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /**
     * Export users matching the same filters as the list API to an xlsx download.
     * NOTE: declared before /{id} semantics matter — Spring prefers exact path
     * matches, so /export never collides with /{id}.
     */
    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    public org.springframework.http.ResponseEntity<byte[]> export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) Integer status) {
        byte[] body = requireBatch().export(keyword, orgId, status);
        String name = "users_" + java.time.LocalDate.now() + ".xlsx";
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + name + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(XLSX))
                .body(body);
    }

    @GetMapping("/import-template")
    @PreAuthorize("hasRole('ADMIN')")
    public org.springframework.http.ResponseEntity<byte[]> importTemplate() {
        byte[] body = requireBatch().importTemplate();
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=\"user_import_template.xlsx\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(XLSX))
                .body(body);
    }

    /** Multipart xlsx upload; partial success — per-row errors reported back. */
    @PostMapping(value = "/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<io.github.shiyi.auth.user.service.UserImportExportService.ImportResult> importUsers(
            @org.springframework.web.bind.annotation.RequestPart("file")
            org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        if (file == null || file.isEmpty()) {
            throw new io.github.shiyi.auth.common.exception.BizException(
                    io.github.shiyi.auth.common.api.ErrorCode.BAD_REQUEST, "文件为空");
        }
        try (java.io.InputStream in = file.getInputStream()) {
            return ApiResult.ok(requireBatch().importUsers(in));
        }
    }

    private io.github.shiyi.auth.user.service.UserImportExportService requireBatch() {
        io.github.shiyi.auth.user.service.UserImportExportService svc = batch.getIfAvailable();
        if (svc == null) {
            throw new io.github.shiyi.auth.common.exception.BizException(
                    io.github.shiyi.auth.common.api.ErrorCode.BAD_REQUEST,
                    "批量导入/导出仅在数据库模式下可用（请以 jdbc profile 启动）");
        }
        return svc;
    }
}
