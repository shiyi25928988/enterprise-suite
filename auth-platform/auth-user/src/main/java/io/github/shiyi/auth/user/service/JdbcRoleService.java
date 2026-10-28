package io.github.shiyi.auth.user.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.github.shiyi.auth.common.api.ErrorCode;
import io.github.shiyi.auth.common.exception.BizException;
import io.github.shiyi.auth.user.dto.RoleRequest;
import io.github.shiyi.auth.user.entity.SysRole;
import io.github.shiyi.auth.user.mapper.SysRoleMapper;

import org.springframework.transaction.annotation.Transactional;

public class JdbcRoleService implements RoleService {

    private final SysRoleMapper roleMapper;

    public JdbcRoleService(SysRoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    @Override
    public List<SysRole> list() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .orderByAsc(SysRole::getId));
    }

    @Override
    @Transactional
    public SysRole create(RoleRequest request) {
        Long dup = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, request.roleCode()));
        if (dup != null && dup > 0) {
            throw new BizException(ErrorCode.CONFLICT, "role code already exists: " + request.roleCode());
        }
        SysRole role = new SysRole();
        role.setRoleCode(request.roleCode());
        role.setRoleName(request.roleName());
        role.setDataScope(request.dataScope());
        role.setStatus(1);
        roleMapper.insert(role);
        return role;
    }

    @Override
    @Transactional
    public SysRole update(Long id, String roleName, Integer dataScope, Integer status) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "role not found: " + id);
        }
        if (roleName != null) role.setRoleName(roleName);
        if (dataScope != null) role.setDataScope(dataScope);
        if (status != null) role.setStatus(status);
        roleMapper.updateById(role);
        return role;
    }
}
