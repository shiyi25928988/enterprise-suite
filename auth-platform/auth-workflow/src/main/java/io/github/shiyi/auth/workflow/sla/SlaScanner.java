package io.github.shiyi.auth.workflow.sla;

import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.shiyi.auth.workflow.notify.WorkflowNotifier;


/**
 * W2: SLA overdue scanner — a fixed-delay sweep over active tasks whose
 * BPMN-declared due date has passed, publishing a {@code task.overdue} event
 * per task (exactly once, via a comment marker). This is deliberately
 * polling instead of boundary timer events: timers die with the engine's async
 * executor (disabled in this deployment), and reminder policy then lives with
 * the platform, not the process XML.
 */
public class SlaScanner {

    private static final Logger log = LoggerFactory.getLogger(SlaScanner.class);
    /** Comment marker written after the first overdue notification. */
    private static final String MARKER = "sla-overdue:";

    private final TaskService tasks;
    private final WorkflowNotifier notifier;

    public SlaScanner(TaskService tasks, WorkflowNotifier notifier) {
        this.tasks = tasks;
        this.notifier = notifier;
    }

    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelayString = "${auth.flow.sla.scan-interval-ms:60000}",
            initialDelayString = "${auth.flow.sla.initial-delay-ms:20000}")
    public void scan() {
        Date now = new Date();
        List<Task> overdue = tasks.createTaskQuery()
                .active()
                .taskDueBefore(now)
                .list();
        for (Task task : overdue) {
            if (alreadyNotified(task)) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("taskId", task.getId());
            payload.put("taskName", task.getName());
            payload.put("processInstanceId", task.getProcessInstanceId());
            payload.put("assignee", task.getAssignee() == null ? "" : task.getAssignee());
            payload.put("dueDate", task.getDueDate() == null ? null
                    : task.getDueDate().toInstant().atZone(ZoneId.systemDefault()).toString());
            payload.put("overdueMs", now.getTime() - task.getDueDate().getTime());
            notifier.notify("task.overdue", payload);
            tasks.addComment(task.getId(), task.getProcessInstanceId(),
                    MARKER + task.getId());
            log.info("SLA: task {} ({}) overdue by {} ms — notified",
                    task.getId(), task.getName(), now.getTime() - task.getDueDate().getTime());
        }
    }

    private boolean alreadyNotified(Task task) {
        // comments are process-scoped; the marker carries the task id, so one
        // notification per task even though the scanner runs repeatedly
        return tasks.getProcessInstanceComments(task.getProcessInstanceId()).stream()
                .map(c -> c.getFullMessage())
                .anyMatch(m -> m != null && m.contains(MARKER + task.getId()));
    }
}
