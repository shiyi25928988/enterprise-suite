package io.github.shiyi.auth.user.sync;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.github.shiyi.auth.user.entity.SysOrg;
import io.github.shiyi.auth.user.entity.SysUser;
import io.github.shiyi.auth.user.mapper.SysOrgMapper;
import io.github.shiyi.auth.user.mapper.SysUserMapper;
import io.github.shiyi.auth.user.notify.NotificationService;
import io.github.shiyi.auth.user.service.JdbcOrgService;

/**
 * Orchestrates one provider's {@link DirectorySnapshot} into sys_org/sys_user.
 *
 * <p>Mapping rules:</p>
 * <ul>
 *   <li>orgs are matched by (external_source, external_id); new ones get
 *       org_code = {@code <source>:<externalId>} and a materialized path like
 *       any locally created org (parent chain is resolved via external ids).</li>
 *   <li>parent moves are NOT applied (moving a subtree requires recomputing
 *       every descendant path — recorded as a warning, same stance as
 *       JdbcOrgService).</li>
 *   <li>users are matched the same way; username = external id (skipped with a
 *       warning if a local user already owns that username). WeCom users also
 *       mirror the userid into wecomUserid so the M5a QR-login auto-bind links
 *       the same account. Initial password is a random unusable bcrypt value.</li>
 * </ul>
 *
 * <p>Not transactional as a whole: each row commits on its own so one bad
 * record cannot roll back a 5000-person directory; problems land in
 * {@link SyncResult#warnings}.</p>
 */
public class UserSyncService {

    private static final Logger log = LoggerFactory.getLogger(UserSyncService.class);

    private final SysOrgMapper orgMapper;
    private final SysUserMapper userMapper;
    private final Map<String, DirectoryProvider> providers;
    private final ObjectProvider<NotificationService> notifications;
    private final Map<String, SyncResult> lastRuns = new HashMap<>();
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    public UserSyncService(SysOrgMapper orgMapper, SysUserMapper userMapper,
                           List<DirectoryProvider> providers,
                           ObjectProvider<NotificationService> notifications) {
        this.orgMapper = orgMapper;
        this.userMapper = userMapper;
        this.providers = providers.stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(DirectoryProvider::source, p -> p));
        this.notifications = notifications;
    }

    /** @return all registered (enabled) sources, e.g. ["dingtalk","wecom"] */
    public Set<String> sources() {
        return providers.keySet();
    }

    public SyncResult lastRun(String source) {
        return lastRuns.get(source);
    }

    public SyncResult sync(String source) {
        DirectoryProvider provider = providers.get(source);
        if (provider == null) {
            throw new IllegalArgumentException("no directory provider enabled for source: " + source);
        }

        List<String> warnings = new ArrayList<>();
        DirectorySnapshot snapshot;
        try {
            snapshot = provider.fetch();
        } catch (Exception e) {
            log.warn("directory sync fetch failed for {}", source, e);
            SyncResult failed = new SyncResult(source, 0, 0, 0, 0,
                    List.of("fetch failed: " + e), Instant.now());
            lastRuns.put(source, failed);
            notifications.ifAvailable(n -> n.publish("sync.failed", Map.of(
                    "source", source, "error", String.valueOf(e.getMessage()))));
            return failed;
        }

        log.info("directory sync {}: fetched {} orgs / {} users from {}",
                source, snapshot.orgs().size(), snapshot.users().size(), provider.getClass().getSimpleName());

        OrgCounts orgCounts = upsertOrgs(source, snapshot, warnings);
        int[] userCounts = upsertUsers(source, snapshot, orgCounts.byExternalId(), warnings);

        SyncResult result = new SyncResult(source,
                orgCounts.created(), orgCounts.updated(), userCounts[0], userCounts[1], warnings, Instant.now());
        lastRuns.put(source, result);
        log.info("directory sync {}: orgs +{}/~{}, users +{}/~{}, {} warning(s)",
                source, orgCounts.created(), orgCounts.updated(), userCounts[0], userCounts[1], warnings.size());
        notifications.ifAvailable(n -> n.publish("sync.completed", Map.of(
                "source", source,
                "orgsCreated", orgCounts.created(),
                "orgsUpdated", orgCounts.updated(),
                "usersCreated", userCounts[0],
                "usersUpdated", userCounts[1],
                "warnings", warnings.size())));
        return result;
    }

    private record OrgCounts(Map<String, SysOrg> byExternalId, int created, int updated) {
    }

    /** Insert-missing / update-name-and-sort for every org, parents first. */
    private OrgCounts upsertOrgs(String source, DirectorySnapshot snapshot, List<String> warnings) {
        int created = 0;
        int updated = 0;
        Map<String, SysOrg> resolved = new HashMap<>();
        orgMapper.selectList(new LambdaQueryWrapper<SysOrg>()
                        .eq(SysOrg::getExternalSource, source))
                .forEach(o -> resolved.put(o.getExternalId(), o));

        for (ExternalOrg e : orderedOrgs(snapshot, warnings)) {
            Long parentId = 0L;
            String parentPath = "/";
            if (e.parentExternalId() != null) {
                SysOrg parent = resolved.get(e.parentExternalId());
                if (parent == null) {
                    warnings.add("org " + e.externalId() + ": parent " + e.parentExternalId() + " not resolvable, kept as root");
                } else {
                    parentId = parent.getId();
                    parentPath = parent.getOrgPath();
                }
            }

            SysOrg current = resolved.get(e.externalId());
            if (current == null) {
                int parentDepth = parentPath.split("/", -1).length - 2;
                if (parentDepth + 1 > JdbcOrgService.MAX_DEPTH) {
                    warnings.add("org " + e.externalId() + " skipped: would exceed max depth " + JdbcOrgService.MAX_DEPTH);
                    continue;
                }
                SysOrg org = new SysOrg();
                org.setParentId(parentId);
                org.setOrgCode(source + ":" + e.externalId());
                org.setOrgName(e.name() == null ? e.externalId() : e.name());
                org.setOrgType(5);
                org.setSortNo(e.sortNo() == null ? 0 : e.sortNo());
                org.setStatus(1);
                org.setExternalSource(source);
                org.setExternalId(e.externalId());
                orgMapper.insert(org);
                org.setOrgPath(parentPath + org.getId() + "/");
                orgMapper.updateById(org);
                resolved.put(e.externalId(), org);
                created++;
            } else {
                boolean changed = false;
                if (e.name() != null && !e.name().equals(current.getOrgName())) {
                    current.setOrgName(e.name());
                    changed = true;
                }
                if (e.sortNo() != null && !e.sortNo().equals(current.getSortNo())) {
                    current.setSortNo(e.sortNo());
                    changed = true;
                }
                if (!Objects.equals(current.getParentId(), parentId)) {
                    warnings.add("org " + e.externalId() + ": parent moved in source; parent moves are not applied (subtree path recompute out of scope)");
                }
                if (changed) {
                    orgMapper.updateById(current);
                    updated++;
                }
            }
        }
        return new OrgCounts(resolved, created, updated);
    }

    /** Parents-before-children ordering; cycles are broken as root-level with a warning. */
    private List<ExternalOrg> orderedOrgs(DirectorySnapshot snapshot, List<String> warnings) {
        Map<String, ExternalOrg> byId = new HashMap<>();
        snapshot.orgs().forEach(o -> byId.put(o.externalId(), o));
        List<ExternalOrg> pending = new ArrayList<>(snapshot.orgs());
        List<ExternalOrg> ordered = new ArrayList<>(pending.size());
        Set<String> placed = new LinkedHashSet<>();
        while (!pending.isEmpty()) {
            int before = pending.size();
            var it = pending.iterator();
            while (it.hasNext()) {
                ExternalOrg o = it.next();
                String parent = o.parentExternalId();
                if (parent == null || !byId.containsKey(parent) || placed.contains(parent)) {
                    ordered.add(o);
                    placed.add(o.externalId());
                    it.remove();
                }
            }
            if (pending.size() == before) {
                ExternalOrg stuck = pending.removeFirst();
                warnings.add("org cycle at " + stuck.externalId() + "; importing as root");
                ordered.addFirst(stuck);
                placed.add(stuck.externalId());
            }
        }
        return ordered;
    }

    private int[] upsertUsers(String source, DirectorySnapshot snapshot,
                              Map<String, SysOrg> orgByExt, List<String> warnings) {
        int created = 0;
        int updated = 0;
        // sys_user.source: 1 local 2 LDAP 3 WeCom 4 DingTalk 5 CSV
        int userSource = switch (source) {
            case "wecom" -> 3;
            case "dingtalk" -> 4;
            case "csv" -> 5;
            default -> 4;
        };

        Map<String, SysUser> existingByExt = new HashMap<>();
        userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getExternalSource, source))
                .forEach(u -> existingByExt.put(u.getExternalId(), u));

        for (ExternalUser u : snapshot.users()) {
            try {
                SysOrg org = u.primaryOrgExternalId() == null ? null
                        : orgByExt.get(u.primaryOrgExternalId());
                if (u.primaryOrgExternalId() != null && org == null) {
                    warnings.add("user " + u.externalId() + ": org " + u.primaryOrgExternalId() + " not resolvable, left without org");
                }
                SysUser current = existingByExt.get(u.externalId());
                if (current == null) {
                    Long dup = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getUsername, u.externalId()));
                    if (dup != null && dup > 0) {
                        warnings.add("user " + u.externalId() + " skipped: username already taken by a local account");
                        continue;
                    }
                    SysUser nu = new SysUser();
                    nu.setUsername(u.externalId());
                    nu.setRealName(u.name() == null ? u.externalId() : u.name());
                    nu.setMobile(u.mobile());
                    nu.setEmployeeNo(u.employeeNo());
                    nu.setOrgId(org == null ? null : org.getId());
                    nu.setSource(userSource);
                    nu.setStatus(1);
                    // random unusable secret: this account authenticates via QR / SSO,
                    // password login stays effectively locked until an admin resets it
                    nu.setPassword("{bcrypt}" + bcrypt.encode(UUID.randomUUID().toString()));
                    nu.setExternalSource(source);
                    nu.setExternalId(u.externalId());
                    if ("wecom".equals(source)) {
                        nu.setWecomUserid(u.externalId());
                    }
                    userMapper.insert(nu);
                    created++;
                } else {
                    boolean changed = false;
                    if (u.name() != null && !u.name().equals(current.getRealName())) {
                        current.setRealName(u.name());
                        changed = true;
                    }
                    if (u.mobile() != null && !u.mobile().equals(current.getMobile())) {
                        current.setMobile(u.mobile());
                        changed = true;
                    }
                    if (u.employeeNo() != null && !u.employeeNo().equals(current.getEmployeeNo())) {
                        current.setEmployeeNo(u.employeeNo());
                        changed = true;
                    }
                    if (org != null && !org.getId().equals(current.getOrgId())) {
                        current.setOrgId(org.getId());
                        changed = true;
                    }
                    if ("wecom".equals(source) && !u.externalId().equals(current.getWecomUserid())) {
                        current.setWecomUserid(u.externalId());
                        changed = true;
                    }
                    if (changed) {
                        userMapper.updateById(current);
                        updated++;
                    }
                }
            } catch (Exception e) {
                warnings.add("user " + u.externalId() + " failed: " + e.getMessage());
            }
        }
        return new int[]{created, updated};
    }
}
