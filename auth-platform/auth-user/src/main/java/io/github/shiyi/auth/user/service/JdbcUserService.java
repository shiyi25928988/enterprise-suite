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
    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    public JdbcUserService(SysUserMapper userMapper, SysOrgMapper orgMapper,
                           SysUserRoleMapper userRoleMapper) {
        this.userMapper = userMapper;
        this.orgMapper = orgMapper;
        this.userRoleMapper = userRoleMapper;
    }

    // ---------- queries ----------

    @Override
    public Optional<UserDTO> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id)).map(this::toDto);
    }

    @Override
    public List<UserDTO> findAll() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .orderByAsc(SysUser::getId))
                .stream().map(this::toDto).toList();
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
        List<UserDTO> records = result.getRecords().stream().map(this::toDto).toList();
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
        requireUser(id);
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .set(SysUser::getStatus, status)
                .set(status == 1, SysUser::getFailCount, 0)); // re-enabling clears the fail counter
    }

    @Override
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        requireUser(id);
        PasswordPolicy.validate(newPassword);
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .set(SysUser::getPassword, encoder.encode(newPassword))
                .set(SysUser::getPasswordUpdateTime, LocalDateTime.now()));
    }

    @Override
    @Transactional
    public void assignRoles(Long id, List<Long> roleIds) {
        requireUser(id);
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
        return new UserDTO(user.getId(), user.getUsername(), user.getRealName(),
                user.getOrgId(), user.getStatus(), user.getLastLoginTime());
    }
}
