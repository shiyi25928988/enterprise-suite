package io.github.shiyi.auth.workflow.ai;

import java.util.Set;

/**
 * Process-variable key contract shared by the service, the AI delegate and
 * the frontend (variable names surface as-is in task details).
 */
public final class WorkflowVar {

    /** Set by WorkflowService.start — the platform username of the initiator. */
    public static final String STARTER = "starter";
    /** Decision variable driving the BPMN gateway after the human task. */
    public static final String APPROVED = "approved";
    public static final String AI_SUGGESTION = "aiSuggestion";
    public static final String AI_REASON = "aiReason";

    /** Never sent to the LLM / never rendered as form fields. */
    public static final Set<String> RESERVED = Set.of(
            STARTER, APPROVED, AI_SUGGESTION, AI_REASON);

    private WorkflowVar() {
    }
}
