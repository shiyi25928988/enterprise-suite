package io.github.shiyi.auth.user.notify;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.shiyi.auth.user.entity.SysMessage;
import io.github.shiyi.auth.user.mapper.SysMessageMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the inbox channel's two hard parts: recipient resolution (the event
 * payload is the routing contract) and idempotency (the SLA scanner re-fires
 * every 30 s). Persistence stays mocked — the row shape is trivial.
 */
class InboxChannelTest {

    private final SysMessageMapper mapper = mock(SysMessageMapper.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final InboxChannel channel = new InboxChannel(mapper, jdbc);
    private final NotifyProperties.Contact contact = new NotifyProperties.Contact();

    private void userExists(String username) {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(username))).thenReturn(1);
    }

    private void roleMembers(String role, String... usernames) {
        when(jdbc.queryForList(anyString(), eq(String.class), eq(role))).thenReturn(List.of(usernames));
    }

    @Test
    @DisplayName("task.assigned with a known assignee lands one row on that user")
    void assigneeRouting() {
        userExists("zhangsan");
        when(mapper.insert(any(SysMessage.class))).thenReturn(1);

        channel.deliver(contact, "task.assigned", Map.of(
                "taskId", "42",
                "taskName", "总监审批",
                "processInstanceId", "7001",
                "assignee", "zhangsan",
                "candidateGroups", List.of()));

        ArgumentCaptor<SysMessage> captor = ArgumentCaptor.forClass(SysMessage.class);
        verify(mapper).insert(captor.capture());
        SysMessage m = captor.getValue();
        assertThat(m.getRecipient()).isEqualTo("zhangsan");
        assertThat(m.getType()).isEqualTo("event");
        assertThat(m.getEvent()).isEqualTo("task.assigned");
        assertThat(m.getTitle()).contains("总监审批");
        assertThat(m.getRefType()).isEqualTo("task");
        assertThat(m.getRefId()).isEqualTo("42");
        assertThat(m.getLink()).isEqualTo("/flow/todo");
    }

    @Test
    @DisplayName("group tasks expand candidateGroups to every enabled member")
    void candidateGroupRouting() {
        roleMembers("EMPLOYEE", "alice", "bob");
        when(mapper.insert(any(SysMessage.class))).thenReturn(1);

        channel.deliver(contact, "task.assigned", Map.of(
                "taskId", "7", "taskName", "主管审批", "processInstanceId", "7002",
                "assignee", "", "candidateGroups", List.of("EMPLOYEE")));

        ArgumentCaptor<SysMessage> captor = ArgumentCaptor.forClass(SysMessage.class);
        verify(mapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(SysMessage::getRecipient)
                .containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    @DisplayName("an unknown assignee routes nowhere instead of guessing")
    void unknownAssigneeIsDropped() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("ghost"))).thenReturn(0);

        channel.deliver(contact, "task.assigned", Map.of(
                "taskId", "9", "taskName", "x", "processInstanceId", "7003",
                "assignee", "ghost", "candidateGroups", List.of()));

        verify(mapper, times(0)).insert(any(SysMessage.class));
    }

    @Test
    @DisplayName("events without a recipient clue go to every enabled ADMIN")
    void adminFallback() {
        roleMembers("ADMIN", "root");
        when(mapper.insert(any(SysMessage.class))).thenReturn(1);

        channel.deliver(contact, "sync.completed", Map.of(
                "source", "csv", "orgsCreated", 0, "orgsUpdated", 3,
                "usersCreated", 1, "usersUpdated", 2, "warnings", 0));

        ArgumentCaptor<SysMessage> captor = ArgumentCaptor.forClass(SysMessage.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getRecipient()).isEqualTo("root");
        assertThat(captor.getValue().getTitle()).contains("目录同步完成");
    }

    @Test
    @DisplayName("account.locked notifies the locked user themself")
    void lockedUserRouting() {
        userExists("alice");
        when(mapper.insert(any(SysMessage.class))).thenReturn(1);

        channel.deliver(contact, "account.locked", Map.of(
                "username", "alice", "failCount", 5, "loginType", "password", "ip", "10.0.0.9"));

        ArgumentCaptor<SysMessage> captor = ArgumentCaptor.forClass(SysMessage.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getRecipient()).isEqualTo("alice");
        assertThat(captor.getValue().getTitle()).contains("锁定");
    }

    @Test
    @DisplayName("a re-render of a delivered event is swallowed, not propagated")
    void duplicateDeliveryIsIdempotent() {
        userExists("zhangsan");
        when(mapper.insert(any(SysMessage.class))).thenThrow(new DuplicateKeyException("uk_msg_event"));

        assertThatCode(() -> channel.deliver(contact, "task.overdue", Map.of(
                "taskId", "42", "taskName", "总监审批", "processInstanceId", "7001",
                "assignee", "zhangsan", "candidateGroups", List.of())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("role expansion failure degrades to no delivery, never an exception")
    void roleExpansionFailureIsContained() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq("ADMIN")))
                .thenThrow(new IllegalStateException("db gone"));

        assertThatCode(() -> channel.deliver(contact, "sync.failed",
                Map.of("source", "ldap", "error", "boom")))
                .doesNotThrowAnyException();

        verify(mapper, times(0)).insert(any(SysMessage.class));
    }
}
