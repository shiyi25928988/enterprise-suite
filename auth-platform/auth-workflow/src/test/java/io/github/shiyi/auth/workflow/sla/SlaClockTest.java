package io.github.shiyi.auth.workflow.sla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.junit.jupiter.api.Test;

/**
 * BPMN due dates are written as {@code ${slaClock.hours(48)}}; a negative value
 * is the supported way to create an already-overdue task, which is how the SLA
 * scanner is exercised without waiting hours.
 *
 * <p>Assertions use a millisecond tolerance on purpose: the helpers return
 * {@link Date}, which is millisecond-precise, while {@link Instant#now()} is
 * not — an exact boundary comparison would fail on rounding alone.</p>
 */
class SlaClockTest {

    /** Date is millisecond-precise; allow a little slack on both sides. */
    private static final long TOLERANCE_MS = 50;

    private final SlaClock clock = new SlaClock();

    @Test
    void positiveOffsetsLandInTheFuture() {
        Date due = clock.hours(48);

        assertThat(due.toInstant()).isCloseTo(
                Instant.now().plus(48, ChronoUnit.HOURS), within(TOLERANCE_MS, ChronoUnit.MILLIS));
    }

    @Test
    void minuteOffsetsAreConvertedNotAppended() {
        Date due = clock.minutes(90);

        assertThat(due.toInstant()).isCloseTo(
                Instant.now().plus(90, ChronoUnit.MINUTES), within(TOLERANCE_MS, ChronoUnit.MILLIS));
    }

    @Test
    void negativeOffsetsLandInThePast() {
        assertThat(clock.hours(-2).toInstant()).isBefore(Instant.now());
        assertThat(clock.minutes(-1).toInstant()).isBefore(Instant.now());
        assertThat(clock.millis(-5_000).toInstant()).isBefore(Instant.now());
    }

    @Test
    void zeroIsNow() {
        assertThat(clock.millis(0).toInstant()).isCloseTo(Instant.now(), within(TOLERANCE_MS, ChronoUnit.MILLIS));
    }
}
