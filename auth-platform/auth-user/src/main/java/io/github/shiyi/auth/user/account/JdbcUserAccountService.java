package io.github.shiyi.auth.user.account;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import io.github.shiyi.auth.user.entity.SysLoginLog;
import io.github.shiyi.auth.user.entity.SysUser;
import io.github.shiyi.auth.user.mapper.SysLoginLogMapper;
import io.github.shiyi.auth.user.mapper.SysUserMapper;
import io.github.shiyi.auth.user.notify.NotificationService;

/**
 * JDBC implementation: audit rows, fail counter, auto-lock after 5 failures.
 */
public class JdbcUserAccountService implements UserAccountService {

    static final int MAX_FAIL_COUNT = 5;

    private final SysUserMapper userMapper;
    private final SysLoginLogMapper loginLogMapper;
    /** Maximum password age in days; 0 disables the policy. */
    private final int maxAgeDays;
    /** Optional — auto-lock events fan out here when contact points exist. */
    private final ObjectProvider<NotificationService> notifications;
    /** Optional (M5c) — login anomaly rules; null disables detection. */
    private final io.github.shiyi.auth.user.anomaly.AnomalyDetector anomaly;
    private final org.springframework.security.crypto.password.PasswordEncoder encoder =
            org.springframework.security.crypto.factory.PasswordEncoderFactories
                    .createDelegatingPasswordEncoder();

    public JdbcUserAccountService(SysUserMapper userMapper, SysLoginLogMapper loginLogMapper) {
        this(userMapper, loginLogMapper, 0, null, null);
    }

    public JdbcUserAccountService(SysUserMapper userMapper, SysLoginLogMapper loginLogMapper,
                                  int maxAgeDays) {
        this(userMapper, loginLogMapper, maxAgeDays, null, null);
    }

    public JdbcUserAccountService(SysUserMapper userMapper, SysLoginLogMapper loginLogMapper,
                                  int maxAgeDays, ObjectProvider<NotificationService> notifications) {
        this(userMapper, loginLogMapper, maxAgeDays, notifications, null);
    }

    public JdbcUserAccountService(SysUserMapper userMapper, SysLoginLogMapper loginLogMapper,
                                  int maxAgeDays, ObjectProvider<NotificationService> notifications,
                                  io.github.shiyi.auth.user.anomaly.AnomalyDetector anomaly) {
        this.userMapper = userMapper;
        this.loginLogMapper = loginLogMapper;
        this.maxAgeDays = maxAgeDays;
        this.notifications = notifications;
        this.anomaly = anomaly;
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
                user.getPassword(), user.getRealName(), user.getStatus(),
                user.getMfaEnabled() != null && user.getMfaEnabled() == 1,
                user.getTotpSecret()));
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
                user.getPassword(), user.getRealName(), user.getStatus(),
                user.getMfaEnabled() != null && user.getMfaEnabled() == 1,
                user.getTotpSecret()));
    }

    @Override
    public Optional<UserAccount> findForDingtalkLogin(String dingtalkUserid) {
        // bound column first; sync-imported users carry the same id in
        // external_id (external_source='dingtalk') and bind on first login
        SysUser user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getDingtalkUserid, dingtalkUserid));
        if (user == null) {
            user = userMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getExternalSource, "dingtalk")
                            .eq(SysUser::getExternalId, dingtalkUserid));
        }
        if (user == null) {
            // auto-bind: DingTalk userid matching an existing username
            SysUser candidate = userMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getUsername, dingtalkUserid));
            if (candidate == null) {
                return Optional.empty();
            }
            userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, candidate.getId())
                    .set(SysUser::getDingtalkUserid, dingtalkUserid));
            user = candidate;
        }
        return Optional.of(toAccount(user));
    }

    @Override
    public Optional<UserAccount> findByBadgeCode(String badgeCode) {
        if (badgeCode == null || badgeCode.length() < 16) {
            return Optional.empty(); // badge codes are 43+ chars — cheap reject
        }
        SysUser user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getBadgeCode, badgeCode));
        return user == null ? Optional.empty() : Optional.of(toAccount(user));
    }

    @Override
    public String rotateBadgeCode(Long userId) {
        byte[] raw = new byte[32];
        new java.security.SecureRandom().nextBytes(raw);
        String code = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getBadgeCode, code));
        return code;
    }

    @Override
    public String findBadgeCode(Long userId) {
        SysUser user = userMapper.selectById(userId);
        return user == null ? null : user.getBadgeCode();
    }

    private static UserAccount toAccount(SysUser user) {
        return new UserAccount(user.getId(), user.getUsername(),
                user.getPassword(), user.getRealName(), user.getStatus(),
                user.getMfaEnabled() != null && user.getMfaEnabled() == 1,
                user.getTotpSecret());
    }

    @Override
    public void enableMfa(Long userId, String totpSecret) {
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getMfaEnabled, 1)
                .set(SysUser::getTotpSecret, totpSecret));
    }

    @Override
    public void disableMfa(Long userId) {
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getMfaEnabled, 0)
                .set(SysUser::getTotpSecret, null));
    }

    @Override
    public boolean mustChangePassword(Long userId) {
        SysUser user = userMapper.selectById(userId);
        return user != null && user.getMustChangePassword() != null
                && user.getMustChangePassword() == 1;
    }

    @Override
    public boolean isPasswordExpired(Long userId) {
        if (maxAgeDays <= 0) {
            return false;
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }
        // Rows predating the policy have a NULL stamp — grandfathered, not expired.
        java.time.LocalDateTime changedAt = user.getPasswordUpdateTime();
        return changedAt != null && changedAt.plusDays(maxAgeDays).isBefore(LocalDateTime.now());
    }

    @Override
    public void markMustChangePassword(Long userId) {
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getMustChangePassword, 1));
    }

    @Override
    public boolean matchesPassword(Long userId, String rawPassword) {
        SysUser user = userMapper.selectById(userId);
        return user != null && rawPassword != null
                && encoder.matches(rawPassword, user.getPassword());
    }

    @Override
    public void changePassword(Long userId, String rawPassword) {
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getPassword, encoder.encode(rawPassword))
                .set(SysUser::getPasswordUpdateTime, LocalDateTime.now())
                .set(SysUser::getMustChangePassword, 0)
                .set(SysUser::getFailCount, 0));
    }

    @Override
    public void onLoginSuccess(String loginType, String username, Long userId, String ip, String userAgent) {
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .set(SysUser::getFailCount, 0)
                .set(SysUser::getLastLoginTime, LocalDateTime.now()));
        // detection BEFORE recording: otherwise this very login pollutes the
        // "locations already seen" history and new_location can never fire
        if (anomaly != null) {
            anomaly.onLoginSuccess(username, ip);
        }
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
            notifications.ifAvailable(n -> n.publish("account.locked", Map.of(
                    "username", username,
                    "failCount", user.getFailCount(),
                    "loginType", loginType == null ? "password" : loginType,
                    "ip", ip == null ? "" : ip)));
        }
        record(loginType, username, userId, false, failReason, ip, userAgent);
        if (anomaly != null) {
            anomaly.onLoginFailure(username, ip);
        }
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
