package io.github.shiyi.auth.server.app;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.shiyi.auth.common.api.ApiResult;
import io.github.shiyi.auth.common.api.ErrorCode;
import io.github.shiyi.auth.common.exception.BizException;
import io.github.shiyi.auth.server.auth.PlatformUser;

/**
 * Application onboarding API.
 *
 * <p>Admin endpoints manage the registry (and therefore the OIDC clients);
 * {@code /mine} is the employee-facing portal list and only needs a login.</p>
 */
@RestController
@RequestMapping("/api/v1/apps")
public class AppController {

    private final ObjectProvider<AppRegistryService> registry;

    public AppController(ObjectProvider<AppRegistryService> registry) {
        this.registry = registry;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<List<AppDTO>> list() {
        return ApiResult.ok(service().list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<AppDTO> get(@PathVariable Long id) {
        return ApiResult.ok(service().get(id));
    }

    /** Response carries the client secret in plaintext — shown exactly once. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<AppRegistryService.CreatedApp> create(@Valid @RequestBody AppRequest request) {
        return ApiResult.ok(service().create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<AppDTO> update(@PathVariable Long id, @RequestBody AppRequest request) {
        return ApiResult.ok(service().update(id, request));
    }

    /** body: {"roleIds": [1, 2]} — grants access to the app for these roles. */
    @PutMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<AppDTO> setRoles(@PathVariable Long id,
                                      @RequestBody Map<String, List<Long>> body) {
        return ApiResult.ok(service().setRoles(id, body.get("roleIds")));
    }

    /** body: {"status": 0|1} */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<AppDTO> setStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        if (status == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "status is required");
        }
        return ApiResult.ok(service().setStatus(id, status));
    }

    /** body: {} — returns {"clientSecret": "sas_..."} once. */
    @PostMapping("/{id}/secret")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<Map<String, String>> rotateSecret(@PathVariable Long id) {
        return ApiResult.ok(Map.of("clientSecret", service().rotateSecret(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        service().delete(id);
        return ApiResult.ok();
    }

    /** Applications the current user is allowed to open (portal). */
    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<List<AppDTO>> mine(Authentication authentication) {
        Long userId = null;
        if (authentication != null && authentication.getPrincipal() instanceof PlatformUser user) {
            userId = user.id();
        }
        return ApiResult.ok(service().accessibleFor(userId));
    }

    private AppRegistryService service() {
        AppRegistryService service = registry.getIfAvailable();
        if (service == null) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "application registry requires the jdbc profile (no DataSource configured)");
        }
        return service;
    }
}
