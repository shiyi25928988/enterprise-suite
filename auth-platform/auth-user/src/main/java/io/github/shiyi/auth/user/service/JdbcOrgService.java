package io.github.shiyi.auth.user.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.github.shiyi.auth.common.api.ErrorCode;
import io.github.shiyi.auth.common.exception.BizException;
import io.github.shiyi.auth.user.dto.OrgDTO;
import io.github.shiyi.auth.user.entity.SysOrg;
import io.github.shiyi.auth.user.entity.SysUser;
import io.github.shiyi.auth.user.mapper.SysOrgMapper;
import io.github.shiyi.auth.user.mapper.SysUserMapper;

import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC implementation. org_path materialized path: parent path + id + '/'.
 * NOTE: moving a subtree (changing parent_id) requires recomputing all
 * descendant paths — deliberately out of scope for M3b.
 */
public class JdbcOrgService implements OrgService {

    private final SysOrgMapper orgMapper;
    private final SysUserMapper userMapper;

    public JdbcOrgService(SysOrgMapper orgMapper, SysUserMapper userMapper) {
        this.orgMapper = orgMapper;
        this.userMapper = userMapper;
    }

    @Override
    public List<OrgDTO> tree() {
        List<SysOrg> all = orgMapper.selectList(new LambdaQueryWrapper<SysOrg>()
                .orderByAsc(SysOrg::getSortNo));
        Map<Long, List<OrgDTO>> byParent = all.stream().map(this::toDto)
                .collect(Collectors.groupingBy(OrgDTO::parentId));
        return buildTree(0L, byParent);
    }

    private List<OrgDTO> buildTree(Long parentId, Map<Long, List<OrgDTO>> byParent) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .map(dto -> new OrgDTO(dto.id(), dto.parentId(), dto.orgCode(), dto.orgName(),
                        dto.orgType(), dto.orgPath(), dto.sortNo(), dto.status(),
                        buildTree(dto.id(), byParent)))
                .toList();
    }

    @Override
    @Transactional
    public OrgDTO create(Long parentId, String orgCode, String orgName, Integer orgType, Integer sortNo) {
        Long dup = orgMapper.selectCount(new LambdaQueryWrapper<SysOrg>()
                .eq(SysOrg::getOrgCode, orgCode));
        if (dup != null && dup > 0) {
            throw new BizException(ErrorCode.CONFLICT, "org code already exists: " + orgCode);
        }

        SysOrg parent = null;
        if (parentId != null && parentId != 0) {
            parent = orgMapper.selectById(parentId);
            if (parent == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "parent org not found: " + parentId);
            }
        }

        SysOrg org = new SysOrg();
        org.setParentId(parentId == null ? 0L : parentId);
        org.setOrgCode(orgCode);
        org.setOrgName(orgName);
        org.setOrgType(orgType == null ? 5 : orgType);
        org.setSortNo(sortNo == null ? 0 : sortNo);
        org.setStatus(1);
        orgMapper.insert(org);

        String parentPath = parent == null ? "/" : parent.getOrgPath();
        org.setOrgPath(parentPath + org.getId() + "/");
        orgMapper.updateById(org);
        return toDto(org);
    }

    @Override
    @Transactional
    public OrgDTO update(Long id, String orgName, Integer sortNo, Long leaderId, Integer status) {
        SysOrg org = requireOrg(id);
        if (orgName != null) org.setOrgName(orgName);
        if (sortNo != null) org.setSortNo(sortNo);
        if (leaderId != null) org.setLeaderId(leaderId);
        if (status != null) org.setStatus(status);
        orgMapper.updateById(org);
        return toDto(org);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysOrg org = requireOrg(id);
        Long children = orgMapper.selectCount(new LambdaQueryWrapper<SysOrg>()
                .eq(SysOrg::getParentId, id));
        if (children != null && children > 0) {
            throw new BizException(ErrorCode.CONFLICT, "org has child organizations");
        }
        Long members = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getOrgId, id));
        if (members != null && members > 0) {
            throw new BizException(ErrorCode.CONFLICT, "org still has members");
        }
        orgMapper.deleteById(id);
    }

    private SysOrg requireOrg(Long id) {
        SysOrg org = orgMapper.selectById(id);
        if (org == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "org not found: " + id);
        }
        return org;
    }

    private OrgDTO toDto(SysOrg org) {
        return new OrgDTO(org.getId(), org.getParentId(), org.getOrgCode(), org.getOrgName(),
                org.getOrgType(), org.getOrgPath(), org.getSortNo(), org.getStatus(), List.of());
    }
}
