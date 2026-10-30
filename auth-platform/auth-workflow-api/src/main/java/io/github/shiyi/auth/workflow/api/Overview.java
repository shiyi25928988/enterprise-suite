package io.github.shiyi.auth.workflow.api;

/**
 * Platform-wide counters for the analytics dashboard.
 *
 * <p>{@code approved} / {@code rejected} only count <em>finished</em>
 * instances: while a countersign is still collecting votes, the instance
 * variable {@code approved} is a draft value, not an outcome.</p>
 */
public record Overview(long total, long running, long finished, long approved,
                       long rejected, long avgCycleMs, long tasksDone, long overdueActive) {
}
