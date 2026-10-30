package io.github.shiyi.auth.user.anomaly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.shiyi.auth.user.entity.SysLoginLog;
import io.github.shiyi.auth.user.mapper.SysLoginLogMapper;
import io.github.shiyi.auth.user.notify.NotificationService;

/**
 * M5c rule behaviour. Both rules are deliberately exact — new_location only
 * fires against a non-empty history, brute_force only at the threshold — and
 * both must stay silent when the audit store misbehaves, because they run
 * inside the login path.
 */
class AnomalyDetectorTest {

    private SysLoginLogMapper loginLogs;
    private NotificationService notifications;
    private AnomalyProperties props;

    @BeforeEach
    void setUp() {
        loginLogs = mock(SysLoginLogMapper.class);
        notifications = mock(NotificationService.class);
        props = new AnomalyProperties();
        props.setEnabled(true);
        props.setLocationWindowDays(30);
        props.setBruteThreshold(5);
        props.setBruteWindowMinutes(10);
    }

    private AnomalyDetector detector() {
        return new AnomalyDetector(loginLogs, notifications, props);
    }

    private void successHistory(String... ips) {
        List<SysLoginLog> rows = new ArrayList<>();
        for (String ip : ips) {
            SysLoginLog row = new SysLoginLog();
            row.setUsername("zhangsan");
            row.setSuccess(1);
            row.setIp(ip);
            rows.add(row);
        }
        when(loginLogs.selectList(any())).thenReturn(rows);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> publishedPayload(String expectedType) {
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(notifications).publish(org.mockito.ArgumentMatchers.eq("login.anomaly"), captor.capture());
        Map<String, Object> payload = captor.getValue();
        assertThat(payload).containsEntry("type", expectedType);
        return payload;
    }

    @Test
    void firstSuccessfulLoginInTheWindowIsNotFlagged() {
        successHistory();

        detector().onLoginSuccess("zhangsan", "10.1.2.9");

        verify(notifications, never()).publish(any(), any());
    }

    @Test
    void unseenSubnetIsFlaggedAsNewLocation() {
        successHistory("10.1.2.9", "10.1.2.14");

        detector().onLoginSuccess("zhangsan", "10.9.9.7");

        Map<String, Object> payload = publishedPayload("new_location");
        assertThat(payload).containsEntry("username", "zhangsan")
                .containsEntry("ip", "10.9.9.7");
        assertThat((String) payload.get("detail")).contains("10.9.9").contains("30 days");
    }

    @Test
    void anotherHostInAKnownSubnetIsNotFlagged() {
        successHistory("10.1.2.9");

        detector().onLoginSuccess("zhangsan", "10.1.2.200");

        verify(notifications, never()).publish(any(), any());
    }

    @Test
    void bruteForceFiresExactlyAtTheThresholdAndNotAfter() {
        // 4 prior failures: one short of the threshold, nothing to report yet
        when(loginLogs.selectCount(any())).thenReturn(4L);
        detector().onLoginFailure("zhangsan", "10.1.2.9");
        verify(notifications, never()).publish(any(), any());

        // the 5th failure is the event
        when(loginLogs.selectCount(any())).thenReturn(5L);
        detector().onLoginFailure("zhangsan", "10.1.2.9");
        assertThat((String) publishedPayload("brute_force").get("detail"))
                .isEqualTo("5 failed logins in 10 minutes");

        // the 6th must stay quiet, otherwise one burst pages five times
        when(loginLogs.selectCount(any())).thenReturn(6L);
        detector().onLoginFailure("zhangsan", "10.1.2.9");
        verify(notifications).publish(any(), any()); // still exactly one call
    }

    @Test
    void disabledDetectorSkipsEveryRule() {
        props.setEnabled(false);
        successHistory("10.1.2.9");
        when(loginLogs.selectCount(any())).thenReturn(5L);

        detector().onLoginSuccess("zhangsan", "10.9.9.7");
        detector().onLoginFailure("zhangsan", "10.9.9.7");

        verify(notifications, never()).publish(any(), any());
    }

    @Test
    void blankAddressesAreIgnoredOnSuccessButFailuresStillCount() {
        successHistory("10.1.2.9");
        when(loginLogs.selectCount(any())).thenReturn(5L);

        detector().onLoginSuccess("zhangsan", "  ");
        verify(notifications, never()).publish(any(), any());

        // a failure without a resolvable address is still a failed attempt
        detector().onLoginFailure("zhangsan", null);
        assertThat(publishedPayload("brute_force")).containsEntry("ip", "");
    }

    @Test
    void auditStoreFailureNeverEscapesIntoTheLogin() {
        when(loginLogs.selectList(any())).thenThrow(new IllegalStateException("db down"));
        when(loginLogs.selectCount(any())).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> {
            detector().onLoginSuccess("zhangsan", "10.9.9.7");
            detector().onLoginFailure("zhangsan", "10.9.9.7");
        }).doesNotThrowAnyException();

        verify(notifications, never()).publish(any(), any());
    }

    @Test
    void locationSegmentKeepsThreeOctetsForIpv4AndEverythingElse() {
        assertThat(AnomalyDetector.locationSegment("203.0.113.7")).isEqualTo("203.0.113");
        assertThat(AnomalyDetector.locationSegment("127.0.0.1")).isEqualTo("127.0.0");
        assertThat(AnomalyDetector.locationSegment("2001:db8::1")).isEqualTo("2001:db8::1");
        assertThat(AnomalyDetector.locationSegment(null)).isEmpty();
    }
}
