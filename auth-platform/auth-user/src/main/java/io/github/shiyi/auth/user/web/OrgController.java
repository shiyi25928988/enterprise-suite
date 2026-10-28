package io.github.shiyi.auth.user.web;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.shiyi.auth.common.api.ApiResult;
import io.github.shiyi.auth.user.dto.OrgDTO;
import io.github.shiyi.auth.user.service.OrgService;

/**
 * Organization management API (development doc 8.4).
 */
@RestController
@RequestMapping("/api/v1/orgs")
public class OrgController {

    private final OrgService orgService;

    public OrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    @GetMapping("/tree")
    public ApiResult<java.util.List<OrgDTO>> tree() {
        return ApiResult.ok(orgService.tree());
    }

    public record CreateOrgRequest(
            @NotBlank @Size(max = 64) String orgCode,
            @NotBlank @Size(max = 128) String orgName,
            Integer orgType,
            Long parentId,
            Integer sortNo) {
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<OrgDTO> create(@jakarta.validation.Valid @RequestBody CreateOrgRequest request) {
        return ApiResult.ok(orgService.create(request.parentId(), request.orgCode(),
                request.orgName(), request.orgType(), request.sortNo()));
    }

    public record UpdateOrgRequest(String orgName, Integer sortNo, Long leaderId, Integer status) {
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<OrgDTO> update(@PathVariable Long id,
                                    @RequestBody UpdateOrgRequest request) {
        return ApiResult.ok(orgService.update(id, request.orgName(), request.sortNo(),
                request.leaderId(), request.status()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        orgService.delete(id);
        return ApiResult.ok();
    }
}
