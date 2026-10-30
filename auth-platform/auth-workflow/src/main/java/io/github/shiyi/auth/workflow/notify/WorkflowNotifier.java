package io.github.shiyi.auth.workflow.notify;

import java.util.Map;

/**
 * Outbound port of the workflow module: "tell someone this happened".
 *
 * <p>The module must not know <em>how</em> an event leaves it. Today the
 * implementation in the composition root forwards to the platform's
 * notification channels (webhook / email); the day the engine is deployed on
 * its own, the same call becomes a message publish or an HTTP call — and no
 * workflow class changes.</p>
 *
 * <p>Implementations must not throw: an unreachable channel can never be
 * allowed to fail an approval. That is why the port returns {@code void} and
 * carries no result the caller could branch on.</p>
 */
@FunctionalInterface
public interface WorkflowNotifier {

    /**
     * @param event   dotted event name, e.g. {@code task.assigned},
     *                {@code task.overdue}
     * @param payload ordered event body; keys are part of the event contract
     */
    void notify(String event, Map<String, Object> payload);
}
