package io.github.shiyi.auth.user.notify;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.shiyi.auth.user.entity.SysMessage;
import io.github.shiyi.auth.user.mapper.SysMessageMapper;

/**
 * M11: the third {@link NotifyChannel} — "inbox". Instead of leaving the
 * platform like webhook / email, it renders each event into per-user rows of
 * {@code sys_message} and a bell in the SPA shows them later.
 *
 * <p>Recipient resolution is a fixed mapping over the event payload (the
 * payloads are part of the event contract, see the publisher sites):
 *
 * <ul>
 *   <li>{@code task.assigned} / {@code task.overdue} — {@code assignee}
 *       (a username) when present, else {@code candidateGroups} expanded to
 *       every enabled member of each role;</li>
 *   <li>{@code account.locked} — the locked user themself;</li>
 *   <li>everything without a recipient clue ({@code sync.completed},
 *       {@code login.anomaly}, ...) — every enabled ADMIN.</li>
 * </ul>
 *
 * <p>Delivery is idempotent through {@code uk_msg_event}: the SLA scanner
 * rescans every 30 seconds and a re-render must not duplicate a row. Any
 * failure is swallowed — an inbox outage must never fail an approval, same
 * contract as the other channels.</p>
 */
public class InboxChannel implements NotifyChannel {

    public static final String TYPE = "inbox";

    private static final Logger log = LoggerFactory.getLogger(InboxChannel.class);

    private final SysMessageMapper messages;
    private final JdbcTemplate jdbc;

    public InboxChannel(SysMessageMapper messages, JdbcTemplate jdbc) {
        this.messages = messages;
        this.jdbc = jdbc;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /** Always ready once wired: the bean only exists in jdbc mode. */
    @Override
    public boolean ready(NotifyProperties.Contact contact) {
        return true;
    }

    @Override
    public void deliver(NotifyProperties.Contact contact, String event, Map<String, Object> body) {
        Set<String> recipients = resolve(event, body);
        if (recipients.isEmpty()) {
            return;
        }
        Draft draft = render(event, body);
        int written = 0;
        for (String recipient : recipients) {
            SysMessage m = new SysMessage();
            m.setRecipient(recipient);
            m.setType("event");
            m.setEvent(event);
            m.setTitle(draft.title());
            m.setBody(draft.body());
            m.setRefType(draft.refType());
            m.setRefId(draft.refId());
            m.setLink(draft.link());
            try {
                written += messages.insert(m);
            } catch (DuplicateKeyException already) {
                // uk_msg_event hit — a re-render of an event this user already has
            } catch (Exception e) {
                log.warn("inbox deliver to {} failed for event {}: {}", recipient, event, e.toString());
            }
        }
        if (written > 0) {
            log.debug("inbox: event {} delivered to {} of {} recipient(s)", event, written, recipients.size());
        }
    }

    // ---------- recipient resolution ----------

    private Set<String> resolve(String event, Map<String, Object> body) {
        return switch (event) {
            case "task.assigned", "task.overdue" -> taskRecipients(body);
            case "account.locked" -> accountRecipients(body);
            default -> adminUsernames();
        }
        ;
    }

    /** Assignee first; group tasks expand each candidate role to its members. */
    private Set<String> taskRecipients(Map<String, Object> body) {
        Set<String> out = new LinkedHashSet<>();
        String assignee = str(body.get("assignee"));
        if (assignee != null && !assignee.isBlank()) {
            if (exists(assignee)) {
                out.add(assignee);
            }
            return out;
        }
        if (body.get("candidateGroups") instanceof List<?> groups) {
            for (Object g : groups) {
                String role = str(g);
                if (role != null && !role.isBlank()) {
                    out.addAll(usernamesByRole(role));
                }
            }
        }
        return out;
    }

    private Set<String> accountRecipients(Map<String, Object> body) {
        String username = str(body.get("username"));
        return username != null && exists(username) ? Set.of(username) : Set.of();
    }

    /** Enabled members of one role code; empty role codes yield nothing. */
    private Set<String> usernamesByRole(String roleCode) {
        try {
            return new LinkedHashSet<>(jdbc.queryForList("""
                    SELECT u.username FROM sys_user u
                    JOIN sys_user_role ur ON ur.user_id = u.id
                    JOIN sys_role r ON r.id = ur.role_id
                    WHERE r.role_code = ? AND r.status = 1 AND r.deleted = 0
                      AND u.status = 1 AND u.deleted = 0
                    """, String.class, roleCode));
        } catch (Exception e) {
            log.warn("inbox: role expansion failed for {}: {}", roleCode, e.toString());
            return Set.of();
        }
    }

    private Set<String> adminUsernames() {
        return usernamesByRole("ADMIN");
    }

    private boolean exists(String username) {
        try {
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_user WHERE username = ? AND status = 1 AND deleted = 0",
                    Integer.class, username);
            return n != null && n > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- rendering ----------

    private record Draft(String title, String body, String refType, String refId, String link) {
    }

    private Draft render(String event, Map<String, Object> body) {
        String taskName = str(body.get("taskName"));
        String taskId = str(body.get("taskId"));
        return switch (event) {
            case "task.assigned" -> new Draft(
                    "您有新的审批任务：" + safe(taskName),
                    "任务 " + safe(taskId) + " 正在等待处理",
                    "task", taskId, "/flow/todo");
            case "task.overdue" -> new Draft(
                    "审批任务已逾期：" + safe(taskName),
                    "任务 " + safe(taskId) + " 已超过截止时间，请尽快处理",
                    "task", taskId, "/flow/todo");
            case "account.locked" -> new Draft(
                    "您的账号已被锁定",
                    "连续登录失败 " + safe(str(body.get("failCount")))
                            + " 次（IP " + safe(str(body.get("ip"))) + "），请联系管理员解锁",
                    "user", str(body.get("username")), null);
            case "login.anomaly" -> new Draft(
                    "检测到异常登录行为",
                    str(body.get("type")) + "：" + safe(str(body.get("detail")))
                            + "（IP " + safe(str(body.get("ip"))) + "）",
                    "user", str(body.get("username")), null);
            case "sync.completed" -> new Draft(
                    "目录同步完成：" + safe(str(body.get("source"))),
                    "组织 +" + str(body.get("orgsCreated")) + "/~" + str(body.get("orgsUpdated"))
                            + "，用户 +" + str(body.get("usersCreated")) + "/~" + str(body.get("usersUpdated"))
                            + "，" + str(body.get("warnings")) + " 条警告",
                    "sync", str(body.get("source")), null);
            case "sync.failed" -> new Draft(
                    "目录同步失败：" + safe(str(body.get("source"))),
                    safe(str(body.get("error"))),
                    "sync", str(body.get("source")), null);
            default -> new Draft(event, null, null, null, null);
        };
    }

    /** Null-safe string for payload values that may be absent. */
    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String safe(String v) {
        return v == null ? "-" : v;
    }
}
