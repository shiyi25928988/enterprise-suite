package io.github.shiyi.auth.user.service;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import io.github.shiyi.auth.user.entity.SysMessage;
import io.github.shiyi.auth.user.entity.SysUser;
import io.github.shiyi.auth.user.mapper.SysMessageMapper;
import io.github.shiyi.auth.user.mapper.SysUserMapper;
import io.github.shiyi.auth.user.mapper.SysUserRoleMapper;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * M11: read side of the in-app message center. Rows are written by
 * {@code InboxChannel} (events) and {@link #announce} (admin broadcasts);
 * everything here is scoped to the recipient's username — there is no
 * cross-user read, ever.
 */
public class MessageService {

    private final SysMessageMapper messages;
    private final SysUserMapper users;
    private final SysUserRoleMapper userRoles;
    private final JdbcTemplate jdbc;

    public MessageService(SysMessageMapper messages, SysUserMapper users,
                          SysUserRoleMapper userRoles, JdbcTemplate jdbc) {
        this.messages = messages;
        this.users = users;
        this.userRoles = userRoles;
        this.jdbc = jdbc;
    }

    /** One inbox page, newest first. */
    public Page<SysMessage> inbox(String recipient, boolean unreadOnly, int page, int size) {
        LambdaQueryWrapper<SysMessage> q = new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getRecipient, recipient)
                .orderByDesc(SysMessage::getId);
        if (unreadOnly) {
            q.isNull(SysMessage::getReadAt);
        }
        return messages.selectPage(new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)), q);
    }

    public long unreadCount(String recipient) {
        return messages.selectCount(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getRecipient, recipient)
                .isNull(SysMessage::getReadAt));
    }

    /** Marks one row read; false when it does not belong to the caller. */
    public boolean markRead(long id, String recipient) {
        return messages.update(null, new LambdaUpdateWrapper<SysMessage>()
                .eq(SysMessage::getId, id)
                .eq(SysMessage::getRecipient, recipient)
                .isNull(SysMessage::getReadAt)
                .set(SysMessage::getReadAt, LocalDateTime.now())) > 0;
    }

    public int markAllRead(String recipient) {
        return messages.update(null, new LambdaUpdateWrapper<SysMessage>()
                .eq(SysMessage::getRecipient, recipient)
                .isNull(SysMessage::getReadAt)
                .set(SysMessage::getReadAt, LocalDateTime.now()));
    }

    /**
     * Admin broadcast. {@code roleCode} null → every enabled user; otherwise
     * the enabled members of that role. Rows are plain announcements, so the
     * event uniqueness key does not apply (both event columns are NULL).
     */
    public int announce(String sender, String title, String body, String link, String roleCode) {
        List<String> recipients = roleCode == null || roleCode.isBlank()
                ? enabledUsernames()
                : enabledRoleMembers(roleCode);
        int written = 0;
        for (String recipient : recipients) {
            SysMessage m = new SysMessage();
            m.setRecipient(recipient);
            m.setType("announcement");
            m.setTitle(title);
            m.setBody(body);
            m.setLink(link);
            m.setSender(sender);
            written += messages.insert(m);
        }
        return written;
    }

    /** Retention sweep — see {@link MessageRetention}. */
    public int purge(LocalDateTime readBefore, LocalDateTime unreadBefore) {
        return messages.purgeBefore(readBefore, unreadBefore);
    }

    private List<String> enabledUsernames() {
        return users.selectList(new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getUsername)
                .eq(SysUser::getStatus, 1)
                .eq(SysUser::getDeleted, 0))
                .stream().map(SysUser::getUsername).toList();
    }

    private List<String> enabledRoleMembers(String roleCode) {
        return jdbc.queryForList("""
                SELECT u.username FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id = u.id
                JOIN sys_role r ON r.id = ur.role_id
                WHERE r.role_code = ? AND r.status = 1 AND r.deleted = 0
                  AND u.status = 1 AND u.deleted = 0
                """, String.class, roleCode);
    }
}
