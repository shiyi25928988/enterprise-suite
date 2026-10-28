package io.github.shiyi.auth.user.service;

import java.util.List;

import io.github.shiyi.auth.user.dto.OrgDTO;

/**
 * Organization service (dual mode). Deleting an org that still has children
 * or members must fail with CONFLICT.
 */
public interface OrgService {

    List<OrgDTO> tree();

    OrgDTO create(Long parentId, String orgCode, String orgName, Integer orgType, Integer sortNo);

    OrgDTO update(Long id, String orgName, Integer sortNo, Long leaderId, Integer status);

    void delete(Long id);
}
