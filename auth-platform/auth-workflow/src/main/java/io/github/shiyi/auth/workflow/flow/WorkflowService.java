package io.github.shiyi.auth.workflow.flow;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;

import io.github.shiyi.auth.common.api.ErrorCode;
import io.github.shiyi.auth.workflow.ai.WorkflowVar;
import io.github.shiyi.auth.common.exception.BizException;
/**
 * W1: approval-flow core operations on top of the embedded Flowable engine.
 * Identity stays in the platform — assignees are platform usernames, candidate
 * groups are platform role codes referenced straight from the BPMN
 * ({@code flowable:candidateGroups="ADMIN"}); nothing is mirrored into
 * Flowable's own IDM tables.
 *
 * <p>Process variables double as the business form payload: the SPA submits
 * the rendered form as a flat string/number map and the AI review node reads
 * the same variables.</p>
 */
public class WorkflowService {

    private final RepositoryService repository;
    private final RuntimeService runtime;
    private final TaskService tasks;
    private final HistoryService history;

    public WorkflowService(RepositoryService repository, RuntimeService runtime,
                           TaskService tasks, HistoryService history) {
        this.repository = repository;
        this.runtime = runtime;
        this.tasks = tasks;
        this.history = history;
    }

    // ---------- definitions ----------

    public record DefInfo(String id, String key, String name, int version,
                          boolean suspended, String deploymentId) {
    }

    public DefInfo deploy(String name, String bpmnXml) {
        // Flowable only parses resources whose name carries a BPMN suffix —
        // without it the deployment lands in ACT_RE_DEPLOYMENT with zero
        // process definitions, which is a silent no-op worth failing loudly.
        String resource = name == null || name.isBlank() ? "process" : name;
        if (!resource.endsWith(".bpmn20.xml") && !resource.endsWith(".bpmn")) {
            resource += ".bpmn20.xml";
        }
        var deployment = repository.createDeployment()
                .name(name)
                .addString(resource, bpmnXml)
                .deploy();
        var definition = repository.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();
        if (definition == null) {
            throw new IllegalStateException("deployment contained no executable BPMN process");
        }
        return toDefInfo(definition);
    }

    public List<DefInfo> definitions() {
        return repository.createProcessDefinitionQuery()
                .latestVersion()
                .orderByProcessDefinitionKey().asc()
                .list().stream()
                .map(WorkflowService::toDefInfo)
                .toList();
    }

    private static DefInfo toDefInfo(ProcessDefinition d) {
        return new DefInfo(d.getId(), d.getKey(), d.getName(), d.getVersion(),
                d.isSuspended(), d.getDeploymentId());
    }

    // ---------- starting ----------

    public record StartResult(String processInstanceId, String businessKey) {
    }

    public StartResult start(String processKey, String initiator, String businessKey,
                             Map<String, Object> variables) {
        var vars = new java.util.HashMap<String, Object>(variables == null ? Map.of() : variables);
        vars.put(WorkflowVar.STARTER, initiator);
        var builder = runtime.createProcessInstanceBuilder()
                .processDefinitionKey(processKey)
                .variables(vars);
        if (businessKey != null && !businessKey.isBlank()) {
            builder.businessKey(businessKey);
        }
        // Flowable stamps startUserId from this, which powers "startedBy" queries
        Authentication.setAuthenticatedUserId(initiator);
        try {
            var instance = builder.start();
            return new StartResult(instance.getId(), instance.getBusinessKey());
        } finally {
            Authentication.setAuthenticatedUserId(null);
        }
    }

    // ---------- task views ----------

    public record TaskView(String id, String name, String assignee,
                           String processInstanceId, String processDefinitionId,
                           java.time.LocalDateTime createTime, boolean aiReviewed) {
    }

    public List<TaskView> todo(String username, List<String> roles) {
        List<Task> list = tasks.createTaskQuery()
                .active()
                .or()
                .taskAssignee(username)
                .taskCandidateGroupIn(roles)
                .endOr()
                .orderByTaskCreateTime().desc()
                .list();
        return list.stream().map(WorkflowService::toTaskView).toList();
    }

    private static TaskView toTaskView(Task t) {
        return new TaskView(t.getId(), t.getName(), t.getAssignee(),
                t.getProcessInstanceId(), t.getProcessDefinitionId(),
                java.time.LocalDateTime.ofInstant(t.getCreateTime().toInstant(),
                        java.time.ZoneId.systemDefault()),
                false);
    }

    public TaskView claim(String taskId, String username, List<String> roles) {
        Task task = requireTask(taskId);
        ensureCanAct(task, username, roles);
        tasks.claim(taskId, username);
        return taskView(taskId);
    }

    /** @return the task view after completion bookkeeping (comment recorded). */
    public TaskView complete(String taskId, String username, List<String> roles,
                             boolean approved, String comment) {
        Task task = requireTask(taskId);
        ensureCanAct(task, username, roles);
        // A candidate-group task nobody claimed: claiming first keeps the
        // history assignee meaningful, so "my done" actually lists it.
        if (task.getAssignee() == null) {
            tasks.claim(taskId, username);
            task = requireTask(taskId);
        }
        TaskView view = toTaskView(task);
        if (comment != null && !comment.isBlank()) {
            tasks.addComment(taskId, task.getProcessInstanceId(), username + ": " + comment);
        }
        tasks.setVariable(taskId, WorkflowVar.APPROVED, approved);
        tasks.complete(taskId);
        return view;
    }

    private Task requireTask(String taskId) {
        Task task = tasks.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "task not found (or already finished): " + taskId);
        }
        return task;
    }

    private void ensureCanAct(Task task, String username, List<String> roles) {
        if (username.equals(task.getAssignee())) {
            return;
        }
        boolean candidate = !tasks.createTaskQuery()
                .taskId(task.getId())
                .taskCandidateUser(username)
                .list().isEmpty()
                || (roles != null && !roles.isEmpty() && !tasks.createTaskQuery()
                        .taskId(task.getId())
                        .taskCandidateGroupIn(roles)
                        .list().isEmpty());
        if (!candidate) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "not assignee or candidate of task " + task.getId());
        }
    }

    public TaskView taskView(String taskId) {
        Task t = tasks.createTaskQuery().taskId(taskId).singleResult();
        return t == null ? null : toTaskView(t);
    }

    /** Full detail: task + process variables (form payload, AI suggestion…). */
    public record TaskDetail(TaskView task, Map<String, Object> variables, List<String> comments) {
    }

    public TaskDetail taskDetail(String taskId, String username, List<String> roles) {
        Task t = requireTask(taskId);
        ensureCanAct(t, username, roles);
        Map<String, Object> vars = tasks.getVariables(taskId);
        List<String> comments = tasks.getProcessInstanceComments(t.getProcessInstanceId())
                .stream().map(org.flowable.engine.task.Comment::getFullMessage).toList();
        return new TaskDetail(toTaskView(t), vars, comments);
    }

    // ---------- history ----------

    public List<TaskView> done(String username) {
        List<HistoricTaskInstance> list = history.createHistoricTaskInstanceQuery()
                .finished()
                .taskAssignee(username)
                .orderByHistoricTaskInstanceEndTime().desc()
                .list();
        return list.stream()
                .map(t -> new TaskView(t.getId(), t.getName(), t.getAssignee(),
                        t.getProcessInstanceId(), t.getProcessDefinitionId(),
                        t.getCreateTime() == null ? null
                                : java.time.LocalDateTime.ofInstant(t.getCreateTime().toInstant(),
                                        java.time.ZoneId.systemDefault()),
                        false))
                .toList();
    }

    public record InstanceView(String id, String processKey, String processName,
                               String starter, String businessKey, java.time.LocalDateTime startedAt,
                               boolean finished, String endState) {
    }

    public List<InstanceView> myInstances(String username) {
        return history.createHistoricProcessInstanceQuery()
                .startedBy(username)
                .includeProcessVariables()
                .orderByProcessInstanceStartTime().desc()
                .list().stream()
                .map(WorkflowService::toInstanceView)
                .toList();
    }

    public InstanceView instance(String instanceId) {
        HistoricProcessInstance h = history.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId)
                .includeProcessVariables()
                .singleResult();
        if (h == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "process instance not found: " + instanceId);
        }
        return toInstanceView(h);
    }

    private static InstanceView toInstanceView(HistoricProcessInstance h) {
        String endState = "running";
        if (h.getEndTime() != null) {
            Object approved = h.getProcessVariables() == null ? null
                    : h.getProcessVariables().get(WorkflowVar.APPROVED);
            endState = Boolean.TRUE.equals(approved) ? "approved"
                    : Boolean.FALSE.equals(approved) ? "rejected" : "finished";
        }
        return new InstanceView(h.getId(), h.getProcessDefinitionKey(), h.getProcessDefinitionName(),
                h.getStartUserId(), h.getBusinessKey(),
                java.time.LocalDateTime.ofInstant(h.getStartTime().toInstant(),
                        java.time.ZoneId.systemDefault()),
                h.getEndTime() != null, endState);
    }

    /** @return true when the instance is still running (variables live in runtime). */
    public Map<String, Object> instanceVariables(String instanceId) {
        var inst = runtime.createProcessInstanceQuery().processInstanceId(instanceId).singleResult();
        if (inst != null) {
            return runtime.getVariables(instanceId);
        }
        var hist = history.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId)
                .includeProcessVariables()
                .singleResult();
        return hist == null ? Map.of() : hist.getProcessVariables();
    }
}
