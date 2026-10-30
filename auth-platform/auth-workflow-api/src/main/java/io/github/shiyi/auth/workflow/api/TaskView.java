package io.github.shiyi.auth.workflow.api;

import java.time.LocalDateTime;

/**
 * A work item as the platform sees it.
 *
 * @param assignee        current holder, null while the task is unclaimed
 * @param owner           original assignee, set only while a delegation is
 *                        pending or resolved
 * @param aiReviewed      true when the AI pre-review node already ran for this
 *                        instance
 * @param delegationState Flowable's delegation state ("pending" / "resolved"),
 *                        null for plain tasks
 * @param dueDate         SLA due date from the BPMN, null when unset
 * @param progress        countersign progress "done/total", null for
 *                        single-instance tasks
 */
public record TaskView(String id, String name, String assignee, String owner,
                       String processInstanceId, String processDefinitionId,
                       LocalDateTime createTime, boolean aiReviewed,
                       String delegationState, LocalDateTime dueDate,
                       String progress) {
}
