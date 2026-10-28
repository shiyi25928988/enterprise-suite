package io.github.shiyi.auth.user.service;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.github.shiyi.auth.user.dto.UserDTO;
import io.github.shiyi.auth.user.entity.SysUser;
import io.github.shiyi.auth.user.mapper.SysUserMapper;

/**
 * JDBC implementation backed by sys_user (MyBatis-Plus).
 */
class JdbcUserService implements UserService {

    private final SysUserMapper userMapper;

    JdbcUserService(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Optional<UserDTO> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id)).map(this::toDto);
    }

    @Override
    public List<UserDTO> findAll() {
        return userMapper.selectList(
                        new LambdaQueryWrapper<SysUser>().orderByAsc(SysUser::getId))
                .stream().map(this::toDto).toList();
    }

    private UserDTO toDto(SysUser user) {
        return new UserDTO(user.getId(), user.getUsername(), user.getRealName(),
                user.getOrgId(), user.getStatus(), user.getLastLoginTime());
    }
}
