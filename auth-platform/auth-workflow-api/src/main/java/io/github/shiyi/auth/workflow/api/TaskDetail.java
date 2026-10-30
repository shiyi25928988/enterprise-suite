package io.github.shiyi.auth.workflow.api;

import java.util.List;
import java.util.Map;

/**
 * Full detail for one task: the view row, the process variables (form payload,
 * AI suggestion…) and the process comments that make up the audit trail.
 */
public record TaskDetail(TaskView task, Map<String, Object> variables, List<String> comments) {
}
