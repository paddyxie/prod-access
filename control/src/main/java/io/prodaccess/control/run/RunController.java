package io.prodaccess.control.run;

import io.prodaccess.control.security.CurrentUser;
import io.prodaccess.control.task.TaskService.SaveInput;
import io.prodaccess.control.task.TaskService;
import io.prodaccess.control.task.TaskVersion;
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
    private final TaskService tasks;

    RunController(RunService runs, TaskService tasks) {
        this.runs = runs;
        this.tasks = tasks;
    }

    record Submit(String taskId, Integer version, Map<String, Object> params) {}

    record AdHoc(String script, Map<String, Object> params) {}

    record Call(String toolId, Map<String, Object> request) {}

    @GetMapping
    List<Run> list(@RequestParam(required = false) RunStatus status, @RequestParam(required = false) RunKind kind,
                   @RequestParam(required = false) String taskId, @RequestParam(required = false) String submittedBy,
                   @RequestParam(defaultValue = "100") int limit) {
        return runs.list(new RunService.Filter(status, kind, taskId, submittedBy), limit);
    }

    @GetMapping("/{id}")
    Run get(@PathVariable String id) {
        return runs.get(id);
    }

    @GetMapping("/{id}/events")
    List<RunLogEntry> events(@PathVariable String id, @RequestParam(defaultValue = "0") long after) {
        return runs.events(id, after);
    }

    @GetMapping("/{id}/tool-calls")
    List<ToolCallRecord> toolCalls(@PathVariable String id) {
        return runs.toolCalls(id);
    }

    @PostMapping
    Run submit(@RequestBody Submit in) {
        return runs.submit(in.taskId(), in.version(), in.params(), CurrentUser.get());
    }

    /** Ad-hoc script: becomes an unnamed task (can be named later) and is run through the same path. */
    @PostMapping("/adhoc")
    Run adhoc(@RequestBody AdHoc in) {
        String actor = CurrentUser.get();
        TaskVersion v = tasks.create(new SaveInput(null, in.script()), actor);
        return runs.submit(v.taskId(), v.ver(), in.params(), actor);
    }

    /** One tool called directly; returns when the call has finished. */
    @PostMapping("/call")
    Run call(@RequestBody Call in) {
        return runs.call(in.toolId(), in.request(), CurrentUser.get());
    }

    @PostMapping("/{id}/cancel")
    Run cancel(@PathVariable String id) {
        return runs.cancel(id, CurrentUser.get());
    }
}
