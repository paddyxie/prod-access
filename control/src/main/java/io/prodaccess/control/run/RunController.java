package io.prodaccess.control.run;

import io.prodaccess.control.security.CurrentUser;
import io.prodaccess.control.workflow.WorkflowService.SaveInput;
import io.prodaccess.control.workflow.WorkflowService;
import io.prodaccess.control.workflow.WorkflowVersion;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The actor is always the authenticated caller, never taken from the request body. */
@RestController
@RequestMapping("/api/runs")
class RunController {
    private final RunService runs;
    private final WorkflowService workflows;
    private final ExecutionAudit evidence;

    RunController(RunService runs, WorkflowService workflows, ExecutionAudit evidence) {
        this.runs = runs;
        this.workflows = workflows;
        this.evidence = evidence;
    }

    record Submit(String workflowId, Integer version, Map<String, Object> params) {}

    record AdHoc(String script, Map<String, Object> params) {}

    record Call(String toolId, String toolVersionId, Map<String, Object> request) {}

    @GetMapping
    List<Run> list(@RequestParam(required = false) RunStatus status, @RequestParam(required = false) RunKind kind,
                   @RequestParam(required = false) String workflowId, @RequestParam(required = false) String submittedBy,
                   @RequestParam(defaultValue = "100") int limit) {
        return runs.list(new RunService.Filter(status, kind, workflowId, submittedBy), limit);
    }

    @GetMapping("/{id}")
    Run get(@PathVariable String id) {
        return runs.get(id);
    }

    @GetMapping("/{id}/inputs")
    RunService.ExecutionInput input(@PathVariable String id) { return runs.input(id); }

    @GetMapping("/{id}/events")
    List<RunLogEntry> events(@PathVariable String id, @RequestParam(defaultValue = "0") long after) {
        return runs.events(id, after);
    }

    @GetMapping("/{id}/tool-calls")
    List<ToolCallRecord> toolCalls(@PathVariable String id) {
        return runs.toolCalls(id);
    }

    @GetMapping("/{id}/api-calls")
    List<ApiCallRecord> apiCalls(@PathVariable String id) { runs.get(id); return evidence.apiCalls(id); }

    @GetMapping("/{id}/actions")
    List<ExecutionAction> actions(@PathVariable String id) { runs.get(id); return evidence.actions(id); }

    @PostMapping
    Run submit(@RequestBody Submit in) {
        return runs.submit(in.workflowId(), in.version(), in.params(), CurrentUser.get());
    }

    /** Ad-hoc script: becomes an unnamed workflow (can be named later) and is run through the same path. */
    @PostMapping("/adhoc")
    Run adhoc(@RequestBody AdHoc in) {
        String actor = CurrentUser.get();
        WorkflowVersion v = workflows.create(new SaveInput(null, in.script()), actor);
        return runs.submit(v.workflowId(), v.ver(), in.params(), actor);
    }

    /** One tool called directly; returns when the call has finished. */
    @PostMapping("/call")
    Run call(@RequestBody Call in) {
        return runs.call(in.toolId(), in.toolVersionId(), in.request(), CurrentUser.get());
    }

    @PostMapping("/{id}/cancel")
    Run cancel(@PathVariable String id) {
        return runs.cancel(id, CurrentUser.get());
    }
}
