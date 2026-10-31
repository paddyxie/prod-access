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

    record Submit(String taskId, Integer version, Map<String, Object> params, String confirmClient) {}

    record AdHoc(String clientId, String script, Map<String, Object> params, String confirmClient) {}

    @GetMapping
    List<Run> list(@RequestParam(required = false) RunStatus status, @RequestParam(required = false) String taskId,
                   @RequestParam(required = false) String clientId, @RequestParam(required = false) String submittedBy,
                   @RequestParam(defaultValue = "100") int limit) {
        return runs.list(new RunService.Filter(status, taskId, clientId, submittedBy), limit);
    }

    @GetMapping("/{id}")
    Run get(@PathVariable String id) {
        return runs.get(id);
    }

    @GetMapping("/{id}/events")
    List<RunLogEntry> events(@PathVariable String id, @RequestParam(defaultValue = "0") long after) {
        return runs.events(id, after);
    }

    @GetMapping("/{id}/api-calls")
    List<ApiCallRecord> apiCalls(@PathVariable String id) {
        return runs.apiCalls(id);
    }

    @PostMapping
    Run submit(@RequestBody Submit in) {
        return runs.submit(in.taskId(), in.version(), in.params(), in.confirmClient(), CurrentUser.get());
    }

    /** Ad-hoc script: becomes an unnamed task (can be named later) and is run through the same path. */
    @PostMapping("/adhoc")
    Run adhoc(@RequestBody AdHoc in) {
        String actor = CurrentUser.get();
        TaskVersion v = tasks.create(new SaveInput(null, in.clientId(), in.script()), actor);
        return runs.submit(v.taskId(), v.ver(), in.params(), in.confirmClient(), actor);
    }

    @PostMapping("/{id}/approve")
    Run approve(@PathVariable String id) {
        return runs.approve(id, CurrentUser.get());
    }

    @PostMapping("/{id}/reject")
    Run reject(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        return runs.reject(id, body == null ? null : body.get("reason"), CurrentUser.get());
    }

    @PostMapping("/{id}/cancel")
    Run cancel(@PathVariable String id) {
        return runs.cancel(id, CurrentUser.get());
    }
}
