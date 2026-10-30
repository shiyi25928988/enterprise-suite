package io.github.shiyi.auth.workflow.api;

import java.time.LocalDateTime;

/**
 * One process instance as its initiator sees it.
 *
 * @param endState "approved" / "rejected" once finished, null while running
 */
public record InstanceView(String id, String processKey, String processName,
                           String starter, String businessKey, LocalDateTime startedAt,
                           boolean finished, String endState) {
}
