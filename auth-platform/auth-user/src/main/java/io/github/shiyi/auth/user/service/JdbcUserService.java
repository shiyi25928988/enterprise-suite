package io.github.shiyi.auth.user.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import io.github.shiyi.auth.common.api.ErrorCode;
import io.github.shiyi.auth.common.api.PageResult;
import io.github.shiyi.auth.common.exception.BizException;
import io.github.shiyi.auth.user.dto.UserCreateRequest;
import io.github.shiyi.auth.user.dto.UserDTO;
import io.github.shiyi.auth.user.dto.UserUpdateRequest;
import io.github.shiyi.auth.user.entity.SysOrg;
import io.github.shiyi.auth.user.entity.SysUser;
import io.github.shiyi.auth.user.entity.SysUserRole;
import io.github.shiyi.auth.user.mapper.SysOrgMapper;
import io.github.shiyi.auth.user.mapper.SysUserMapper;
import io.github.shiyi.auth.user.mapper.SysUserRoleMapper;
import io.github.shiyi.auth.user.util.PasswordPolicy;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC implementation (MyBatis-Plus).
 */
public class JdbcUserService implements UserService {

    private final SysUserMapper userMapper;
    private final SysOrgMapper orgMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    public JdbcUserService(SysUserMapper userMapper, SysOrgMapper orgMapper,
                           SysUserRoleMapper userRoleMapper) {
        this(userMapper, orgMapper, userRoleMapper, null);
    }

    public JdbcUserService(SysUserMapper userMapper, SysOrgMapper orgMapper,
                           SysUserRoleMapper userRoleMapper,
                           org.springframework.context.ApplicationEventPublisher events) {
        this.userMapper = userMapper;
        this.orgMapper = orgMapper;
        this.userRoleMapper = userRoleMapper;
        this.events = events;
    }

    // ---------- queries ----------

    @Override
    public Optional<UserDTO> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id)).map(this::toDto);
    }

    @Override
    public io.github.shiyi.auth.user.dto.UserDetailDTO detail(Long id) {
        SysUser user = requireUser(id);
        SysOrg org = user.getOrgId() == null ? null : orgMapper.selectById(user.getOrgId());
        List<io.github.shiyi.auth.user.dto.UserDetailDTO.RoleRef> roles =
                userRoleMapper.selectRoleDetailsByUserId(id).stream()
                        .map(r -> new io.github.shiyi.auth.user.dto.UserDetailDTO.RoleRef(
                                ((Number) r.get("id")).longValue(),
                                (String) r.get("role_code"),
                                (String) r.get("role_name")))
                        .toList();
        return new io.github.shiyi.auth.user.dto.UserDetailDTO(
                user.getId(), user.getUsername(), user.getRealName(),
                user.getEmployeeNo(), user.getMobile(), user.getEmail(),
                user.getOrgId(), org == null ? null : org.getOrgName(),
                org == null ? null : org.getOrgPath(),
                user.getSource(), user.getStatus(), user.getMfaEnabled(),
                user.getMustChangePassword(), user.getLastLoginTime(),
                user.getPasswordUpdateTime(), user.getCreateTime(), user.getUpdateTime(),
                roles);
    }

    @Override
    @Transactional
    public void moveOrg(Long id, Long orgId) {
        SysUser user = requireUser(id);
        if (orgId != null && orgMapper.selectById(orgId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "organization not found: " + orgId);
        }
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .set(SysUser::getOrgId, orgId));
    }

    @Override
    public List<UserDTO> findAll() {
        return toDtos(userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .orderByAsc(SysUser::getId)));
    }

    @Override
    public PageResult<UserDTO> page(int page, int size, String keyword, Long orgId, Integer status) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SysUser::getUsername, keyword)
                        .or().like(SysUser::getRealName, keyword))
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getId);

        if (orgId != null) {
            SysOrg org = orgMapper.selectById(orgId);
            if (org != null) {
                List<Long> orgIds = orgMapper.selectSubtreeIds(org.getOrgPath());
                wrapper.in(SysUser::getOrgId, orgIds);
            } else {
                wrapper.eq(SysUser::getOrgId, orgId);
            }
        }

        Page<SysUser> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        List<UserDTO> records = toDtos(result.getRecords());
        return new PageResult<>(records, result.getTotal(), page, size);
    }

    // ---------- mutations ----------

    @Override
    @Transactional
    public UserDTO create(UserCreateRequest request) {
        Long existing = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.username()));
        if (existing != null && existing > 0) {
            throw new BizException(ErrorCode.CONFLICT, "username already exists: " + request.username());
        }
        PasswordPolicy.validate(request.password());

        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPassword(encoder.encode(request.password()));
        user.setRealName(request.realName());
        user.setEmployeeNo(request.employeeNo());
        user.setMobile(request.mobile());
        user.setEmail(request.email());
        user.setOrgId(request.orgId());
        user.setSource(1);
        user.setStatus(1);
        user.setFailCount(0);
        user.setMustChangePassword(1); // admin-chosen initial secret must be replaced
        user.setPasswordUpdateTime(LocalDateTime.now());
        userMapper.insert(user);

        if (request.roleIds() != null && !request.roleIds().isEmpty()) {
            assignRoles(user.getId(), request.roleIds());
        }
        return toDto(user);
    }

    @Override
    @Transactional
    public UserDTO update(Long id, UserUpdateRequest request) {
        SysUser user = requireUser(id);
        user.setRealName(request.realName());
        user.setEmployeeNo(request.employeeNo());
        user.setMobile(request.mobile());
        user.setEmail(request.email());
        user.setOrgId(request.orgId());
        userMapper.updateById(user);
        return toDto(user);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1 && status != 2)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "status must be 0, 1 or 2");
        }
        SysUser target = requireUser(id);
        if (status != 1) {
            String me = currentUsername();
            if (me != null && me.equals(target.getUsername())) {
                throw new BizException(ErrorCode.CONFLICT, "不能停用/锁定当前登录的账号");
            }
            guardLastActiveAdmin(target);
        }
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .set(SysUser::getStatus, status)
                .set(status == 1, SysUser::getFailCount, 0)); // re-enabling clears the fail counter
        if (status != 1) {
            // a disabled/locked account must not keep working on an old session
            revokeSessions(target.getUsername(), "status-" + status);
        }
    }

    @Override
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        SysUser target = requireUser(id);
        PasswordPolicy.validate(newPassword);
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .set(SysUser::getPassword, encoder.encode(newPassword))
                .set(SysUser::getPasswordUpdateTime, LocalDateTime.now())
                // the admin knows this secret, so the holder has to replace it
                .set(SysUser::getMustChangePassword, 1));
        revokeSessions(target.getUsername(), "password-reset");
    }

    @Override
    @Transactional
    public void assignRoles(Long id, List<Long> roleIds) {
        SysUser target = requireUser(id);
        Long adminRoleId = userMapper.selectAdminRoleId();
        boolean keepsAdmin = adminRoleId != null && roleIds != null && roleIds.contains(adminRoleId);
        if (!keepsAdmin) {
            String me = currentUsername();
            if (me != null && me.equals(target.getUsername())) {
                throw new BizException(ErrorCode.CONFLICT, "不能移除当前登录账号的管理员角色");
            }
            guardLastActiveAdmin(target);
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, id));
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(id);
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            }
        }
    }

    // ---------- helpers ----------

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "user not found: " + id);
        }
        return user;
    }

    private UserDTO toDto(SysUser user) {
        return toDtos(List.of(user)).get(0);
    }

    /**
     * Maps a page of rows with ONE role query instead of one per row — the old
     * per-row lookup cost 21 SQL round trips for a 20-row page and thousands
     * for an export.
     */
    private List<UserDTO> toDtos(List<SysUser> users) {
        if (users.isEmpty()) {
            return List.of();
        }
        List<Long> ids = users.stream().map(SysUser::getId).toList();
        java.util.Map<Long, List<Long>> rolesByUser = userRoleMapper.selectByUserIds(ids).stream()
                .collect(java.util.stream.Collectors.groupingBy(SysUserRole::getUserId,
                        java.util.stream.Collectors.mapping(SysUserRole::getRoleId,
                                java.util.stream.Collectors.toList())));
        return users.stream()
                .map(u -> new UserDTO(u.getId(), u.getUsername(), u.getRealName(),
                        u.getOrgId(), u.getStatus(), u.getLastLoginTime(),
                        rolesByUser.getOrDefault(u.getId(), List.of())))
                .toList();
    }

    private String currentUsername() {        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }

    private void revokeSessions(String username, String reason) {
        if (events != null) {
            events.publishEvent(
                    new io.github.shiyi.auth.user.event.UserAccessRevokedEvent(username, reason));
        }
    }

    /**
     * Refuses an operation that would leave the platform with no enabled
     * administrator — otherwise a single mis-click locks everybody out for good.
     */
    private void guardLastActiveAdmin(SysUser target) {
        List<Long> activeAdmins = userMapper.selectActiveAdminIds();
        if (activeAdmins.size() == 1 && activeAdmins.contains(target.getId())) {
            throw new BizException(ErrorCode.CONFLICT,
                    "至少需要保留一个启用的管理员，不能停用或降权最后一个管理员");
        }
    }
}
