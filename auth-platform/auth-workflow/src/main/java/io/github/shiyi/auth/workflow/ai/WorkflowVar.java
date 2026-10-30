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

    // ---- W2 countersign (multi-instance) variables ----
    /** Comma-separated usernames collected at start, converted to a List. */
    public static final String APPROVERS = "approvers";
    /** Share of approvals that ends the countersign early (0–1, default 1). */
    public static final String APPROVAL_RATIO = "approvalRatio";
    /** Process-level vote ledger, e.g. ["alice=approve", "bob=reject"]. */
    public static final String COUNTERSIGN_VOTES = "countersignVotes";
    /** Set to true by the first reject vote — one veto ends the countersign. */
    public static final String COUNTERSIGN_REJECTED = "csRejected";

    /** Never sent to the LLM / never rendered as form fields. */
    public static final Set<String> RESERVED = Set.of(
            STARTER, APPROVED, AI_SUGGESTION, AI_REASON,
            APPROVERS, APPROVAL_RATIO, COUNTERSIGN_VOTES, COUNTERSIGN_REJECTED);

    private WorkflowVar() {
    }
}
