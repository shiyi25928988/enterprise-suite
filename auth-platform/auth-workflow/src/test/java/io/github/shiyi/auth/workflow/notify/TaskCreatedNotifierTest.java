package io.github.shiyi.auth.workflow.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.identitylink.service.impl.persistence.entity.IdentityLinkEntity;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Pins the event contract the module publishes through {@link WorkflowNotifier}.
 * The event name and payload keys are the interface external channels see
 * (webhook JSON, email body field order), so a rename must be a conscious act.
 */
class TaskCreatedNotifierTest {

    private final WorkflowNotifier notifier = mock(WorkflowNotifier.class);
    private final TaskCreatedNotifier listener = new TaskCreatedNotifier(notifier);

    @Test
    void publishesTaskWithCandidateGroups() {
        IdentityLinkEntity admin = identityLink("ADMIN");
        IdentityLinkEntity adminAgain = identityLink("ADMIN");
        IdentityLinkEntity userLink = mock(IdentityLinkEntity.class); // no group => not a candidate group

        TaskEntity task = task("t-1", "主管审批", "pi-9", null, List.of(admin, adminAgain, userLink));
        listener.onEvent(taskCreated(task));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(notifier).notify(eq("task.assigned"), payload.capture());
        assertThat(payload.getValue())
                .containsEntry("taskId", "t-1")
                .containsEntry("taskName", "主管审批")
                .containsEntry("processInstanceId", "pi-9")
                // unclaimed task: empty string instead of null, so consumers
                // never have to distinguish "no assignee" from "missing field"
                .containsEntry("assignee", "")
                .containsEntry("candidateGroups", List.of("ADMIN")); // distinct
    }

    @Test
    void keepsTheAssigneeWhenAlreadyClaimed() {
        TaskEntity task = task("t-2", "会签审批", "pi-10", "zhangsan", List.of());
        listener.onEvent(taskCreated(task));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(notifier).notify(eq("task.assigned"), payload.capture());
        assertThat(payload.getValue()).containsEntry("assignee", "zhangsan");
    }

    @Test
    void ignoresEventsOtherThanTaskCreated() {
        FlowableEngineEntityEvent event = mock(FlowableEngineEntityEvent.class);
        when(event.getType()).thenReturn(FlowableEngineEventType.TASK_COMPLETED);

        listener.onEvent(event);

        verify(notifier, never()).notify(anyString(), any());
    }

    @Test
    void ignoresCreatedEventsWhoseEntityIsNotATask() {
        FlowableEngineEntityEvent event = mock(FlowableEngineEntityEvent.class);
        when(event.getType()).thenReturn(FlowableEngineEventType.TASK_CREATED);
        when(event.getEntity()).thenReturn("not a task");

        listener.onEvent(event);

        verify(notifier, never()).notify(anyString(), any());
    }

    @Test
    void neverFailsTheEngine() {
        // isFailOnException=false is what keeps a broken webhook from rolling
        // back the transaction that created the task.
        assertThat(listener.isFailOnException()).isFalse();
    }

    private static FlowableEngineEntityEvent taskCreated(TaskEntity task) {
        FlowableEngineEntityEvent event = mock(FlowableEngineEntityEvent.class);
        when(event.getType()).thenReturn(FlowableEngineEventType.TASK_CREATED);
        when(event.getEntity()).thenReturn(task);
        return event;
    }

    private static TaskEntity task(String id, String name, String pi, String assignee,
                                   List<IdentityLinkEntity> links) {
        TaskEntity task = mock(TaskEntity.class);
        when(task.getId()).thenReturn(id);
        when(task.getName()).thenReturn(name);
        when(task.getProcessInstanceId()).thenReturn(pi);
        when(task.getAssignee()).thenReturn(assignee);
        when(task.getIdentityLinks()).thenReturn(links);
        return task;
    }

    private static IdentityLinkEntity identityLink(String groupId) {
        IdentityLinkEntity link = mock(IdentityLinkEntity.class);
        when(link.getGroupId()).thenReturn(groupId);
        return link;
    }
}
