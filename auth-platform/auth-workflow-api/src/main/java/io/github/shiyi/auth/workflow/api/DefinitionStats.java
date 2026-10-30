package io.github.shiyi.auth.workflow.api;

/** Per-definition instance counts and cycle time (all versions pooled by key). */
public record DefinitionStats(String processKey, String name, long instances,
                              long finished, long approved, long rejected,
                              long avgCycleMs) {
}
