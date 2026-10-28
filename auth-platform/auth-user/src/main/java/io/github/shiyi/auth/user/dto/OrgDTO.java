package io.github.shiyi.auth.user.dto;

import java.util.List;

/**
 * Organization tree node.
 */
public record OrgDTO(
        Long id,
        Long parentId,
        String orgCode,
        String orgName,
        Integer orgType,
        String orgPath,
        Integer sortNo,
        Integer status,
        List<OrgDTO> children) {
}
