package io.github.shiyi.auth.workflow.sla;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * W2: expression helper for BPMN due dates —
 * {@code flowable:dueDate="${slaClock.hours(24)}"}. A bean (not a static util)
 * because BPMN expressions resolve against Spring beans in this embedded
 * setup, and negative values are deliberate: {@code slaClock.minutes(-1)}
 * creates an already-overdue task, which is how tests exercise the scanner
 * without waiting.
 */
public class SlaClock {

    public Date hours(long hours) {
        return Date.from(Instant.now().plus(hours, ChronoUnit.HOURS));
    }

    public Date minutes(long minutes) {
        return Date.from(Instant.now().plus(minutes, ChronoUnit.MINUTES));
    }

    /** For tests and edge cases: exactly {@code ms} from now. */
    public Date millis(long ms) {
        return Timestamp.from(Instant.now().plusMillis(ms));
    }
}
