package io.github.shiyi.auth.workflow.api;

/** Cycle time for one activity, taken from the latest definition version only. */
public record NodeStats(String activityId, String name, String type, long count,
                        long avgMs, long minMs, long maxMs) {
}
