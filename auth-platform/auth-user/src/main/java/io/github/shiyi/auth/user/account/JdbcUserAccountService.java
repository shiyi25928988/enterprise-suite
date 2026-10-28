package io.github.shiyi.auth.user.account;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import io.github.shiyi.auth.user.entity.SysLoginLog;
import io.github.shiyi.auth.user.entity.SysUser;
import io.github.shiyi.auth.user.mapper.SysLoginLogMapper;
import io.github.shiyi.auth.user.mapper.SysUserMapper;

/**
 * JDBC implementation: audit rows, fail counter, auto-lock after 5 failures.
 */
class JdbcUserAccountService implements UserAccountService {

    static final int MAX_FAIL_COUNT = 5;

    private final SysUserMapper userMapper;
    private final SysLoginLogMapper loginLogMapper;

    JdbcUserAccountService(SysUserMapper userMapper, SysLoginLogMapper loginLogMapper) {
        this.userMapper = userMapper;
        this.loginLogMapper = loginLogMapper;
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        SysUser user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username));
        if (user == null) {
            return Optional.empty();
        }
        return Optional.of(new UserAccount(user.getId(), user.getUsername(),
                user.getPassword(), user.getRealName(), user.getStatus()));
    }

    @Override
    public List<String> findRoleCodes(Long userId) {
        return userMapper.selectRoleCodes(userId);
    }

    @Override
    public Optional<UserAccount> findForWecomLogin(String wecomUserid) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser> byWecom =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getWecomUserid, wecomUserid);
        SysUser user = userMapper.selectOne(byWecom);
        if (user == null) {
            // auto-bind: WeCom userid matching an existing username (first login)
            SysUser candidate = userMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getUsername, wecomUserid));
            if (candidate == null) {
                return Optional.empty();
            }
            userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, candidate.getId())
                    .set(SysUser::getWecomUserid, wecomUserid));
            user = candidate;
        }
        return Optional.of(new UserAccount(user.getId(), user.getUsername(),
                user.getPassword(), user.getRealName(), user.getStatus()));
    }

    @Override
    public void onLoginSuccess(String loginType, String username, Long userId, String ip, String userAgent) {
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .set(SysUser::getFailCount, 0)
                .set(SysUser::getLastLoginTime, LocalDateTime.now()));
        record(loginType, username, userId, true, null, ip, userAgent);
    }

    @Override
    public boolean onLoginFailure(String loginType, String username, Long userId,
                                  String failReason, String ip, String userAgent) {
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .setSql("fail_count = fail_count + 1"));

        boolean locked = false;
        SysUser user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username));
        if (user != null && user.getFailCount() != null
                && user.getFailCount() >= MAX_FAIL_COUNT && user.getStatus() != null
                && user.getStatus() == 1) {
            userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getUsername, username)
                    .set(SysUser::getStatus, 2));
            locked = true;
        }
        record(loginType, username, userId, false, failReason, ip, userAgent);
        return locked;
    }

    private void record(String loginType, String username, Long userId, boolean success,
                        String failReason, String ip, String userAgent) {
        SysLoginLog log = new SysLoginLog();
        log.setUsername(username);
        log.setUserId(userId);
        log.setLoginType(loginType);
        log.setSuccess(success ? 1 : 0);
        log.setFailReason(failReason);
        log.setIp(ip);
        log.setUserAgent(userAgent);
        log.setCreateTime(LocalDateTime.now());
        loginLogMapper.insert(log);
    }
}
