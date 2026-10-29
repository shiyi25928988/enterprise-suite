package io.github.shiyi.auth.server.session;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.github.shiyi.auth.server.auth.LoginSessionSupport;
import io.github.shiyi.auth.user.entity.SysUser;
import io.github.shiyi.auth.user.mapper.SysUserMapper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * Online-session administration: who is signed in, and kicking them out.
 *
 * <p>Spring Session's indexed repository can only be queried by principal name,
 * not enumerated, so the candidate set comes from {@code sys_user.last_login_time}
 * (recent successful logins) and each candidate is then checked against the
 * live session store. That keeps it correct without maintaining a second index
 * that would drift when sessions expire silently.</p>
 *
 * <p>Without Redis (demo mode) no repository exists: {@code supported=false} is
 * returned and the console explains why instead of pretending nobody is online.</p>
 */
@Service
public class SessionAdminService {

    /** How far back a login can be and still count as "possibly online". */
    private static final Duration LOOKBACK = Duration.ofHours(12);

    private final ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> repository;
    private final ObjectProvider<SysUserMapper> userMapper;

    public SessionAdminService(
            ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> repository,
            ObjectProvider<SysUserMapper> userMapper) {
        this.repository = repository;
        this.userMapper = userMapper;
    }

    public record SessionRow(String sessionId, String username, String realName,
                             String ip, String userAgent, Instant loginAt,
                             Instant lastAccessedAt, long idleSeconds, boolean current) {
    }

    public record SessionList(boolean supported, List<SessionRow> rows) {
    }

    public SessionList list(String currentSessionId, int limit) {
        FindByIndexNameSessionRepository<? extends Session> repo = repository.getIfAvailable();
        SysUserMapper mapper = userMapper.getIfAvailable();
        if (repo == null || mapper == null) {
            return new SessionList(false, List.of());
        }

        List<SysUser> candidates = mapper.selectList(new LambdaQueryWrapper<SysUser>()
                .ge(SysUser::getLastLoginTime, LocalDateTime.now().minus(LOOKBACK))
                .orderByDesc(SysUser::getLastLoginTime)
                .last("LIMIT " + Math.max(1, limit)));

        List<SessionRow> rows = new ArrayList<>();
        for (SysUser candidate : candidates) {
            for (Session session : byPrincipal(repo, candidate.getUsername()).values()) {
                if (session.isExpired()) {
                    continue;
                }
                Instant lastAccessed = session.getLastAccessedTime();
                rows.add(new SessionRow(
                        session.getId(),
                        candidate.getUsername(),
                        candidate.getRealName(),
                        attribute(session, LoginSessionSupport.ATTR_LOGIN_IP),
                        attribute(session, LoginSessionSupport.ATTR_LOGIN_AGENT),
                        session.getCreationTime(),
                        lastAccessed,
                        Duration.between(lastAccessed, Instant.now()).toSeconds(),
                        session.getId().equals(currentSessionId)));
            }
        }
        rows.sort((a, b) -> b.lastAccessedAt().compareTo(a.lastAccessedAt()));
        return new SessionList(true, rows);
    }

    /** @return number of sessions actually dropped */
    public int revokeSession(String sessionId) {
        FindByIndexNameSessionRepository<? extends Session> repo = repository.getIfAvailable();
        if (repo == null || sessionId == null) {
            return 0;
        }
        if (repo.findById(sessionId) == null) {
            return 0;
        }
        repo.deleteById(sessionId);
        return 1;
    }

    /**
     * Drops every session of one account.
     *
     * @param keepSessionId session to spare (the caller's own), null to drop all
     * @return number of sessions dropped
     */
    public int revokeUser(String username, String keepSessionId) {
        FindByIndexNameSessionRepository<? extends Session> repo = repository.getIfAvailable();
        if (repo == null || username == null) {
            return 0;
        }
        int dropped = 0;
        for (Session session : byPrincipal(repo, username).values()) {
            if (keepSessionId != null && keepSessionId.equals(session.getId())) {
                continue;
            }
            repo.deleteById(session.getId());
            dropped++;
        }
        return dropped;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, ? extends Session> byPrincipal(
            FindByIndexNameSessionRepository repo, String username) {
        Map<String, Session> found = (Map<String, Session>) repo.findByPrincipalName(username);
        return found == null ? Map.of() : found;
    }

    private String attribute(Session session, String name) {
        Object value = session.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }
}
