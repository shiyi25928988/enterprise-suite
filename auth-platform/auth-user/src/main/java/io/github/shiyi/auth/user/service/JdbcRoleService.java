package io.github.shiyi.auth.user.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.github.shiyi.auth.common.api.ErrorCode;
import io.github.shiyi.auth.common.exception.BizException;
import io.github.shiyi.auth.user.dto.RoleRequest;
import io.github.shiyi.auth.user.dto.RoleUsageDTO;
import io.github.shiyi.auth.user.entity.SysRole;
import io.github.shiyi.auth.user.entity.SysUserRole;
import io.github.shiyi.auth.user.mapper.SysRoleMapper;
import io.github.shiyi.auth.user.mapper.SysUserRoleMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

public class JdbcRoleService implements RoleService {

    /** Renaming this code away is the deployment's responsibility — deleting stays blocked. */
    public static final String BUILT_IN_ADMIN = "ADMIN";

    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    /** Used only for the sys_app_role reference count — one COUNT, no entity needed. */
    private final JdbcTemplate jdbc;

    public JdbcRoleService(SysRoleMapper roleMapper, SysUserRoleMapper userRoleMapper, JdbcTemplate jdbc) {
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.jdbc = jdbc;
    }

    @Override
    public List<SysRole> list() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .orderByAsc(SysRole::getId));
    }

    @Override
    public List<RoleUsageDTO> usage() {
        Map<Long, Long> byUser = userRoleMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .collect(Collectors.groupingBy(SysUserRole::getRoleId, Collectors.counting()));
        Map<Long, Long> byApp = jdbc.query("""
                SELECT role_id, COUNT(*) AS cnt FROM sys_app_role GROUP BY role_id
                """, rs -> {
            java.util.Map<Long, Long> out = new java.util.HashMap<>();
            while (rs.next()) {
                out.put(rs.getLong("role_id"), rs.getLong("cnt"));
            }
            return out;
        });
        return list().stream()
                .map(r -> new RoleUsageDTO(r.getId(),
                        byUser.getOrDefault(r.getId(), 0L),
                        byApp.getOrDefault(r.getId(), 0L),
                        BUILT_IN_ADMIN.equals(r.getRoleCode())))
                .toList();
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
        SysRole role = requireRole(id);
        if (roleName != null) role.setRoleName(roleName);
        if (dataScope != null) role.setDataScope(dataScope);
        if (status != null) role.setStatus(status);
        roleMapper.updateById(role);
        return role;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysRole role = requireRole(id);
        if (BUILT_IN_ADMIN.equals(role.getRoleCode())) {
            throw new BizException(ErrorCode.CONFLICT, "内置角色不能删除: " + role.getRoleCode());
        }
        Long users = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, id));
        if (users != null && users > 0) {
            throw new BizException(ErrorCode.CONFLICT,
                    "角色仍被 " + users + " 个用户持有，请先移除用户的该角色");
        }
        Integer apps = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_app_role WHERE role_id = ?", Integer.class, id);
        if (apps != null && apps > 0) {
            throw new BizException(ErrorCode.CONFLICT,
                    "角色仍被 " + apps + " 个应用授权，请先在应用接入中移除");
        }
        roleMapper.deleteById(id);
    }

    private SysRole requireRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "role not found: " + id);
        }
        return role;
    }
}
