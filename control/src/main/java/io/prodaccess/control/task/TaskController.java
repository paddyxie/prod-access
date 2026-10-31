package io.prodaccess.control.task;

import io.prodaccess.control.security.CurrentUser;
import io.prodaccess.control.task.TaskService.SaveInput;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
class TaskController {
    private final TaskService tasks;

    TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping
    List<Task> list(@RequestParam(required = false) String q, @RequestParam(required = false) String clientId) {
        return tasks.list(q, clientId);
    }

    @GetMapping("/{id}")
    Task get(@PathVariable String id) {
        return tasks.get(id);
    }

    @PostMapping
    TaskVersion create(@RequestBody SaveInput in) {
        return tasks.create(in, CurrentUser.get());
    }

    @GetMapping("/{id}/versions")
    List<TaskVersion> versions(@PathVariable String id) {
        return tasks.versions(id);
    }

    @GetMapping("/{id}/versions/{ver}")
    TaskVersion version(@PathVariable String id, @PathVariable int ver) {
        return tasks.version(id, ver);
    }

    @PostMapping("/{id}/versions")
    TaskVersion newVersion(@PathVariable String id, @RequestBody SaveInput in) {
        return tasks.newVersion(id, in, CurrentUser.get());
    }

    @PostMapping("/{id}/name")
    Task name(@PathVariable String id, @RequestBody Map<String, String> body) {
        return tasks.saveAs(id, body.get("name"), CurrentUser.get());
    }
}
