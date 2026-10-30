package io.github.shiyi.auth.workflow.api;

/** Identifiers returned when an instance is started. */
public record StartResult(String processInstanceId, String businessKey) {
}
