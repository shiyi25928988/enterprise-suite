package io.github.shiyi.auth.workflow.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.shiyi.auth.common.api.ApiResult;
import io.github.shiyi.auth.common.api.ErrorCode;
import io.github.shiyi.auth.common.exception.BizException;
import io.github.shiyi.auth.workflow.form.FlowFormEntity;
import io.github.shiyi.auth.workflow.form.FlowFormService;
import io.github.shiyi.auth.workflow.flow.WorkflowService;
import io.github.shiyi.auth.workflow.api.DefInfo;
import io.github.shiyi.auth.workflow.api.DefinitionStats;
import io.github.shiyi.auth.workflow.api.InstanceView;
import io.github.shiyi.auth.workflow.api.NodeStats;
import io.github.shiyi.auth.workflow.api.Overview;
import io.github.shiyi.auth.workflow.api.StartResult;
import io.github.shiyi.auth.workflow.api.TaskDetail;
import io.github.shiyi.auth.workflow.api.TaskView;
import io.github.shiyi.auth.workflow.api.TimelineStep;

/**
 * W1: workflow REST surface.
 * <pre>
 *   POST /api/v1/flow/deploy                     deploy a BPMN XML           (ADMIN)
 *   GET  /api/v1/flow/definitions                process definitions         (ADMIN)
 *   GET  /api/v1/flow/forms                      form schemas                (ADMIN)
 *   PUT  /api/v1/flow/forms/{processKey}         upsert a form schema        (ADMIN)
 *   GET  /api/v1/flow/forms/{processKey}         one schema (any logged-in user)
 *   POST /api/v1/flow/instances                  start an instance
 *   GET  /api/v1/flow/todo | /done | /instances/mine
 *   GET  /api/v1/flow/tasks/{id}                 task detail + variables + comments
 *   POST /api/v1/flow/tasks/{id}/claim
 *   POST /api/v1/flow/tasks/{id}/complete        {approved, comment}
 *   POST /api/v1/flow/tasks/{id}/delegate        {user} — W2, ownership handshake
 *   POST /api/v1/flow/tasks/{id}/transfer        {user} — W2, reassign outright
 *   POST /api/v1/flow/tasks/{id}/unclaim         W2, back to the candidate pool
 *   GET  /api/v1/flow/analytics/overview         W2 (ADMIN)
 *   GET  /api/v1/flow/analytics/definitions      W2 (ADMIN)
 *   GET  /api/v1/flow/analytics/nodes/{key}      W2 (ADMIN)
 *   GET  /api/v1/flow/instances/{id}/timeline    W2, activity timeline
 * </pre>
 * Identity comes from the session/JWT — never from the request body.
 */
@RestController
@RequestMapping("/api/v1/flow")
public class FlowController {

    private final ObjectProvider<WorkflowService> workflows;
    private final ObjectProvider<FlowFormService> forms;
    private final ObjectProvider<io.github.shiyi.auth.workflow.analytics.FlowAnalyticsService> analyticsProvider;

    public FlowController(ObjectProvider<WorkflowService> workflows,
                          ObjectProvider<FlowFormService> forms,
                          ObjectProvider<io.github.shiyi.auth.workflow.analytics.FlowAnalyticsService> analyticsProvider) {
        this.workflows = workflows;
        this.forms = forms;
        this.analyticsProvider = analyticsProvider;
    }

    // ---------- definitions / forms (ADMIN) ----------

    public record DeployRequest(String name, String bpmnXml) {
    }

    @PostMapping("/deploy")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<DefInfo> deploy(@RequestBody DeployRequest req) {
        if (req.bpmnXml() == null || req.bpmnXml().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "bpmnXml is required");
        }
        try {
            return ApiResult.ok(service().deploy(req.name(), req.bpmnXml()));
        } catch (RuntimeException e) {
            // Flowable wraps XML validation errors — surface them as 400, not 500
            throw new BizException(ErrorCode.BAD_REQUEST, "deploy failed: " + rootMessage(e));
        }
    }

    @GetMapping("/definitions")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<List<DefInfo>> definitions() {
        return ApiResult.ok(service().definitions());
    }

    /** Anything a logged-in employee may start, with its form name for the picker. */
    @GetMapping("/startable")
    public ApiResult<List<Map<String, Object>>> startable() {
        List<DefInfo> defs = service().definitions().stream()
                .filter(d -> !d.suspended())
                .toList();
        FlowFormService formService = forms.getIfAvailable();
        return ApiResult.ok(defs.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("processKey", d.key());
            m.put("name", d.name());
            m.put("version", d.version());
            FlowFormEntity f = formService == null ? null : formService.get(d.key());
            m.put("formName", f == null ? null : f.getFormName());
            m.put("hasForm", f != null);
            return m;
        }).toList());
    }

    @GetMapping("/forms")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<List<Map<String, Object>>> forms() {
        return ApiResult.ok(formService().list().stream().map(FlowController::formView).toList());
    }

    @GetMapping("/forms/{processKey}")
    public ApiResult<Map<String, Object>> form(@PathVariable String processKey) {
        FlowFormEntity f = formService().get(processKey);
        return ApiResult.ok(f == null ? null : formView(f));
    }

    public record FormUpsertRequest(String formName, String schemaJson) {
    }

    @PutMapping("/forms/{processKey}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<Map<String, Object>> upsertForm(@PathVariable String processKey,
                                                     @RequestBody FormUpsertRequest req,
                                                     Authentication auth) {
        try {
            FlowFormEntity saved = formService().save(processKey, req.formName(),
                    req.schemaJson(), auth.getName());
            return ApiResult.ok(formView(saved));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, e.getMessage());
        }
    }

    private static Map<String, Object> formView(FlowFormEntity f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("processKey", f.getProcessKey());
        m.put("formName", f.getFormName());
        m.put("schemaJson", f.getSchemaJson());
        m.put("updatedBy", f.getUpdatedBy());
        m.put("updatedAt", f.getUpdatedAt());
        return m;
    }

    // ---------- instances ----------

    public record StartRequest(String processKey, String businessKey, Map<String, Object> variables) {
    }

    @PostMapping("/instances")
    public ApiResult<StartResult> start(@RequestBody StartRequest req,
                                                        Authentication auth) {
        // form validation belongs here: schema constraints are business rules
        Map<String, Object> vars;
        try {
            vars = formService().validateAgainstSchema(req.processKey(), req.variables());
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, e.getMessage());
        }
        return ApiResult.ok(service().start(req.processKey(), auth.getName(),
                req.businessKey(), vars));
    }

    @GetMapping("/instances/mine")
    public ApiResult<List<InstanceView>> myInstances(Authentication auth) {
        return ApiResult.ok(service().myInstances(auth.getName()));
    }

    @GetMapping("/instances/{id}")
    public ApiResult<Map<String, Object>> instance(@PathVariable String id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("instance", service().instance(id));
        m.put("variables", service().instanceVariables(id));
        return ApiResult.ok(m);
    }

    // ---------- tasks ----------

    @GetMapping("/todo")
    public ApiResult<List<TaskView>> todo(Authentication auth) {
        return ApiResult.ok(service().todo(auth.getName(), roles(auth)));
    }

    @GetMapping("/done")
    public ApiResult<List<TaskView>> done(Authentication auth) {
        return ApiResult.ok(service().done(auth.getName()));
    }

    @GetMapping("/tasks/{id}")
    public ApiResult<TaskDetail> task(@PathVariable String id, Authentication auth) {
        return ApiResult.ok(service().taskDetail(id, auth.getName(), roles(auth)));
    }

    @PostMapping("/tasks/{id}/claim")
    public ApiResult<TaskView> claim(@PathVariable String id, Authentication auth) {
        return ApiResult.ok(service().claim(id, auth.getName(), roles(auth)));
    }

    public record CompleteRequest(boolean approved, String comment) {
    }

    @PostMapping("/tasks/{id}/complete")
    public ApiResult<TaskView> complete(@PathVariable String id,
                                                        @RequestBody CompleteRequest req,
                                                        Authentication auth) {
        return ApiResult.ok(service().complete(id, auth.getName(), roles(auth),
                req.approved(), req.comment()));
    }

    // ---------- W2: delegation / transfer ----------

    /** On a pending-delegation task this answers back to the delegator. */
    public record TaskActionRequest(String user) {
    }

    @PostMapping("/tasks/{id}/delegate")
    public ApiResult<TaskView> delegate(@PathVariable String id,
                                                        @RequestBody TaskActionRequest req,
                                                        Authentication auth) {
        return ApiResult.ok(service().delegate(id, auth.getName(), roles(auth), req.user()));
    }

    @PostMapping("/tasks/{id}/transfer")
    public ApiResult<TaskView> transfer(@PathVariable String id,
                                                        @RequestBody TaskActionRequest req,
                                                        Authentication auth) {
        return ApiResult.ok(service().transfer(id, auth.getName(), roles(auth), req.user()));
    }

    @PostMapping("/tasks/{id}/unclaim")
    public ApiResult<TaskView> unclaim(@PathVariable String id,
                                                       Authentication auth) {
        return ApiResult.ok(service().unclaim(id, auth.getName(), roles(auth)));
    }

    // ---------- W2: analytics ----------

    @GetMapping("/analytics/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<Overview>
            analyticsOverview() {
        return ApiResult.ok(analytics().overview());
    }

    @GetMapping("/analytics/definitions")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<List<DefinitionStats>>
            analyticsDefinitions() {
        return ApiResult.ok(analytics().definitions());
    }

    @GetMapping("/analytics/nodes/{processKey}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<List<NodeStats>>
            analyticsNodes(@PathVariable String processKey) {
        return ApiResult.ok(analytics().nodes(processKey));
    }

    @GetMapping("/instances/{id}/timeline")
    public ApiResult<List<TimelineStep>>
            timeline(@PathVariable String id) {
        return ApiResult.ok(analytics().timeline(id));
    }

    // ---------- plumbing ----------

    private WorkflowService service() {
        WorkflowService s = workflows.getIfAvailable();
        if (s == null) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "workflow requires the jdbc profile (embedded Flowable engine)");
        }
        return s;
    }

    private FlowFormService formService() {
        FlowFormService s = forms.getIfAvailable();
        if (s == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "workflow requires the jdbc profile");
        }
        return s;
    }

    private io.github.shiyi.auth.workflow.analytics.FlowAnalyticsService analytics() {
        var s = analyticsProvider.getIfAvailable();
        if (s == null) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "workflow requires the jdbc profile (embedded Flowable engine)");
        }
        return s;
    }

    /** ROLE_ADMIN → ADMIN; candidate groups in BPMN use bare role codes. */
    private static List<String> roles(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .toList();
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() == null ? e.toString() : t.getMessage();
    }
}
