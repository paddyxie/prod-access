package io.prodaccess.control.workflow;

import io.prodaccess.control.security.CurrentUser;
import io.prodaccess.control.workflow.WorkflowService.SaveInput;
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
@RequestMapping("/api/workflows")
class WorkflowController {
    private final WorkflowService workflows;

    WorkflowController(WorkflowService workflows) {
        this.workflows = workflows;
    }

    @GetMapping
    List<Workflow> list(@RequestParam(required = false) String q, @RequestParam(required = false) String role) {
        return workflows.list(q, role);
    }

    @GetMapping("/{id}")
    Workflow get(@PathVariable String id) {
        return workflows.get(id);
    }

    @PostMapping
    WorkflowVersion create(@RequestBody SaveInput in) {
        return workflows.create(in, CurrentUser.get());
    }

    @GetMapping("/{id}/versions")
    List<WorkflowVersion> versions(@PathVariable String id) {
        return workflows.versions(id);
    }

    @GetMapping("/{id}/versions/{ver}")
    WorkflowVersion version(@PathVariable String id, @PathVariable int ver) {
        return workflows.version(id, ver);
    }

    @PostMapping("/{id}/versions")
    WorkflowVersion newVersion(@PathVariable String id, @RequestBody SaveInput in) {
        return workflows.newVersion(id, in, CurrentUser.get());
    }

    @PostMapping("/{id}/name")
    Workflow name(@PathVariable String id, @RequestBody Map<String, String> body) {
        return workflows.saveAs(id, body.get("name"), CurrentUser.get());
    }
}
