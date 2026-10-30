package io.github.shiyi.auth.workflow.api;

import java.time.LocalDateTime;

/** One row of an instance's flow timeline: who did what, and for how long. */
public record TimelineStep(String activityId, String name, String type, String assignee,
                           LocalDateTime startTime, LocalDateTime endTime, Long durationMs) {
}
