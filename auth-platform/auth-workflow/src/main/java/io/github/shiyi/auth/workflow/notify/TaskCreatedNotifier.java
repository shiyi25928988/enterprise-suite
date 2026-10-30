package io.github.shiyi.auth.workflow.notify;

import java.util.LinkedHashMap;
import java.util.Map;

import org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;


/**
 * W1-6: bridges engine events into the module's notifier port. Only task creation is published — everyone who can act on a
 * new task learns about it without polling; completion is visible in the
 * history API instead of being spammed to every webhook.
 */
public class TaskCreatedNotifier implements FlowableEventListener {

    private final WorkflowNotifier notifier;

    public TaskCreatedNotifier(WorkflowNotifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public void onEvent(FlowableEvent event) {
        if (event.getType() != FlowableEngineEventType.TASK_CREATED
                || !(event instanceof FlowableEngineEntityEvent entityEvent)
                || !(entityEvent.getEntity() instanceof TaskEntity task)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getId());
        payload.put("taskName", task.getName());
        payload.put("processInstanceId", task.getProcessInstanceId());
        payload.put("assignee", task.getAssignee() == null ? "" : task.getAssignee());
        payload.put("candidateGroups", task.getIdentityLinks() == null ? "" : task.getIdentityLinks()
                .stream()
                .filter(l -> l.getGroupId() != null)
                .map(l -> l.getGroupId())
                .distinct()
                .toList());
        notifier.notify("task.assigned", payload);
    }

    @Override
    public boolean isFailOnException() {
        return false; // notifications must never break the engine
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }

    @Override
    public String getOnTransaction() {
        return null;
    }
}
