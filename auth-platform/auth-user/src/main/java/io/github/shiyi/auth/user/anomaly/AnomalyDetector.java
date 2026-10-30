package io.github.shiyi.auth.user.anomaly;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.github.shiyi.auth.user.entity.SysLoginLog;
import io.github.shiyi.auth.user.mapper.SysLoginLogMapper;
import io.github.shiyi.auth.user.notify.NotificationService;

/**
 * M5c: rule-based login anomaly detection.
 *
 * <ul>
 *   <li><b>new_location</b> — a successful login from an IP /24 (first three
 *       octets) the username has never used in the location window. Fires only
 *       when the user has prior successful logins, so first logins are not
 *       flagged.</li>
 *   <li><b>brute_force</b> — the Nth failed login for one username inside the
 *       brute window (exactly at the threshold, so one burst = one event even
 *       though the scanner of record is the audit log itself).</li>
 * </ul>
 *
 * <p>Both publish {@code login.anomaly} through the M10 contact points.
 * Detection failures must never break an actual login — callers wrap this
 * class and every rule swallows its own exceptions.</p>
 */
public class AnomalyDetector {

    private final SysLoginLogMapper loginLogs;
    private final NotificationService notifications;
    private final AnomalyProperties props;

    public AnomalyDetector(SysLoginLogMapper loginLogs, NotificationService notifications,
                           AnomalyProperties props) {
        this.loginLogs = loginLogs;
        this.notifications = notifications;
        this.props = props;
    }

    public void onLoginSuccess(String username, String ip) {
        if (!props.isEnabled() || username == null || ip == null || ip.isBlank()) {
            return;
        }
        try {
            String segment = locationSegment(ip);
            LocalDateTime since = LocalDateTime.now().minusDays(props.getLocationWindowDays());
            List<SysLoginLog> history = loginLogs.selectList(new LambdaQueryWrapper<SysLoginLog>()
                    .eq(SysLoginLog::getUsername, username)
                    .eq(SysLoginLog::getSuccess, 1)
                    .ge(SysLoginLog::getCreateTime, since));
            if (history.isEmpty()) {
                return; // first (successful) login in the window — nothing to compare
            }
            boolean seen = history.stream().anyMatch(l -> segment.equals(locationSegment(l.getIp())));
            if (!seen) {
                publish("new_location", username, ip,
                        "first login from " + segment + " in " + props.getLocationWindowDays() + " days");
            }
        } catch (RuntimeException e) {
            // anomaly detection must never break the login it is observing
        }
    }

    public void onLoginFailure(String username, String ip) {
        if (!props.isEnabled() || username == null) {
            return;
        }
        try {
            LocalDateTime since = LocalDateTime.now().minusMinutes(props.getBruteWindowMinutes());
            Long failures = loginLogs.selectCount(new LambdaQueryWrapper<SysLoginLog>()
                    .eq(SysLoginLog::getUsername, username)
                    .eq(SysLoginLog::getSuccess, 0)
                    .ge(SysLoginLog::getCreateTime, since));
            // exact threshold: one burst → exactly one anomaly event
            if (failures != null && failures == props.getBruteThreshold()) {
                publish("brute_force", username, ip == null ? "" : ip,
                        failures + " failed logins in " + props.getBruteWindowMinutes() + " minutes");
            }
        } catch (RuntimeException e) {
            // swallow — see above
        }
    }

    /** /24 segment for IPv4 (first three octets); full string otherwise. */
    static String locationSegment(String ip) {
        if (ip == null) {
            return "";
        }
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + "." + parts[2];
        }
        return ip;
    }

    private void publish(String type, String username, String ip, String detail) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("username", username);
        payload.put("ip", ip);
        payload.put("detail", detail);
        notifications.publish("login.anomaly", payload);
    }
}
