package io.github.shiyi.auth.workflow.analytics;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;

import io.github.shiyi.auth.workflow.ai.WorkflowVar;
import io.github.shiyi.auth.workflow.api.DefinitionStats;
import io.github.shiyi.auth.workflow.api.NodeStats;
import io.github.shiyi.auth.workflow.api.Overview;
import io.github.shiyi.auth.workflow.api.TimelineStep;

/**
 * W2: process analytics on top of Flowable history — instance throughput and
 * cycle time overall, per definition and per node. Everything reads ACT_HI_*
 * via the engine's query API; no extra tables, no bespoke aggregation job.
 */
public class FlowAnalyticsService {

    private final HistoryService history;
    private final RuntimeService runtime;
    private final TaskService tasks;
    private final org.flowable.engine.RepositoryService repository;

    public FlowAnalyticsService(HistoryService history, RuntimeService runtime,
                                TaskService tasks, org.flowable.engine.RepositoryService repository) {
        this.history = history;
        this.runtime = runtime;
        this.tasks = tasks;
        this.repository = repository;
    }

    // ---------- overview ----------

    public Overview overview() {
        List<HistoricProcessInstance> all = history.createHistoricProcessInstanceQuery()
                .includeProcessVariables()
                .orderByProcessInstanceStartTime().asc()
                .list();
        long running = 0, finished = 0, approved = 0, rejected = 0;
        long cycleSum = 0, cycleCount = 0;
        for (HistoricProcessInstance h : all) {
            if (h.getEndTime() == null) {
                running++;
                continue;
            }
            finished++;
            if (h.getDurationInMillis() != null) {
                cycleSum += h.getDurationInMillis();
                cycleCount++;
            }
            Object a = h.getProcessVariables() == null ? null
                    : h.getProcessVariables().get(WorkflowVar.APPROVED);
            if (Boolean.TRUE.equals(a)) {
                approved++;
            } else if (Boolean.FALSE.equals(a)) {
                rejected++;
            }
        }
        long overdue = tasks.createTaskQuery().active()
                .taskDueBefore(java.util.Date.from(java.time.Instant.now()))
                .count();
        long tasksDone = history.createHistoricTaskInstanceQuery().finished().count();
        return new Overview(all.size(), running, finished, approved, rejected,
                cycleCount == 0 ? 0 : cycleSum / cycleCount, tasksDone, overdue);
    }

    // ---------- per definition ----------

    public List<DefinitionStats> definitions() {
        List<HistoricProcessInstance> all = history.createHistoricProcessInstanceQuery()
                .includeProcessVariables()
                .list();
        Map<String, List<HistoricProcessInstance>> byKey = new LinkedHashMap<>();
        for (HistoricProcessInstance h : all) {
            byKey.computeIfAbsent(h.getProcessDefinitionKey(), k -> new ArrayList<>()).add(h);
        }
        return byKey.entrySet().stream().map(e -> {
            List<HistoricProcessInstance> list = e.getValue();
            long finished = list.stream().filter(i -> i.getEndTime() != null).count();
            // only finished instances carry a decision — a running countersign
            // instance's `approved` variable is scratch state (per-vote), not
            // an outcome
            long approved = list.stream()
                    .filter(i -> i.getEndTime() != null)
                    .filter(i -> Boolean.TRUE.equals(i.getProcessVariables() == null ? null
                            : i.getProcessVariables().get(WorkflowVar.APPROVED))).count();
            long cycleSum = list.stream()
                    .filter(i -> i.getEndTime() != null && i.getDurationInMillis() != null)
                    .mapToLong(i -> i.getDurationInMillis()).sum();
            long cycleCount = list.stream()
                    .filter(i -> i.getEndTime() != null && i.getDurationInMillis() != null)
                    .count();
            String name = repository.createProcessDefinitionQuery()
                    .processDefinitionKey(e.getKey()).latestVersion().singleResult() instanceof ProcessDefinition d
                            ? d.getName()
                            : e.getKey();
            return new DefinitionStats(e.getKey(), name, list.size(), finished, approved,
                    finished - approved, cycleCount == 0 ? 0 : cycleSum / cycleCount);
        }).toList();
    }

    // ---------- per node ----------

    /**
     * Node cycle times for a definition (latest version only — mixing versions
     * would compare activity ids that no longer exist).
     */
    public List<NodeStats> nodes(String processKey) {
        ProcessDefinition def = repository.createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .latestVersion()
                .singleResult();
        if (def == null) {
            return List.of();
        }
        List<HistoricActivityInstance> acts = history.createHistoricActivityInstanceQuery()
                .processDefinitionId(def.getId())
                .finished()
                .list();
        Map<String, List<HistoricActivityInstance>> byId = new LinkedHashMap<>();
        for (HistoricActivityInstance a : acts) {
            byId.computeIfAbsent(a.getActivityId(), k -> new ArrayList<>()).add(a);
        }
        return byId.entrySet().stream().map(e -> {
            List<HistoricActivityInstance> list = e.getValue();
            long min = Long.MAX_VALUE, max = 0, sum = 0;
            for (HistoricActivityInstance a : list) {
                long d = a.getDurationInMillis() == null ? 0 : a.getDurationInMillis();
                min = Math.min(min, d);
                max = Math.max(max, d);
                sum += d;
            }
            HistoricActivityInstance first = list.get(0);
            return new NodeStats(e.getKey(), first.getActivityName() == null ? e.getKey()
                    : first.getActivityName(), first.getActivityType(), list.size(),
                    sum / list.size(), min == Long.MAX_VALUE ? 0 : min, max);
        }).sorted((a, b) -> Long.compare(b.avgMs(), a.avgMs())).toList();
    }

    // ---------- instance timeline ----------

    public List<TimelineStep> timeline(String instanceId) {
        return history.createHistoricActivityInstanceQuery()
                .processInstanceId(instanceId)
                .orderByHistoricActivityInstanceStartTime().asc()
                .list().stream()
                .map(FlowAnalyticsService::toStep)
                .toList();
    }

    private static TimelineStep toStep(HistoricActivityInstance a) {
        return new TimelineStep(a.getActivityId(),
                a.getActivityName() == null ? a.getActivityId() : a.getActivityName(),
                a.getActivityType(), a.getAssignee(),
                LocalDateTime.ofInstant(a.getStartTime().toInstant(), ZoneId.systemDefault()),
                a.getEndTime() == null ? null
                        : LocalDateTime.ofInstant(a.getEndTime().toInstant(), ZoneId.systemDefault()),
                a.getDurationInMillis());
    }
}
