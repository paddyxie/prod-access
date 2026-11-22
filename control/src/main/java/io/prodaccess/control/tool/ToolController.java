package io.prodaccess.control.tool;

import io.prodaccess.common.protocol.ToolType;
import io.prodaccess.control.security.CurrentUser;
import io.prodaccess.control.tool.ToolService.ToolInput;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tools")
class ToolController {
    private final ToolService tools;

    ToolController(ToolService tools) {
        this.tools = tools;
    }

    @GetMapping
    List<Tool> list(@RequestParam(required = false) String q, @RequestParam(required = false) String role,
                    @RequestParam(required = false) ToolType type) {
        return tools.list(q, role, type);
    }

    @GetMapping("/{id}")
    Tool get(@PathVariable String id) {
        return tools.get(id);
    }

    @GetMapping("/{id}/versions")
    List<Tool> versions(@PathVariable String id) { return tools.versions(id); }

    @PostMapping
    Tool create(@RequestBody ToolInput in) {
        return tools.create(in, CurrentUser.get());
    }

    @PutMapping("/{id}")
    Tool update(@PathVariable String id, @RequestBody ToolInput in) {
        return tools.update(id, in, CurrentUser.get());
    }

    @DeleteMapping("/{id}")
    void delete(@PathVariable String id) {
        tools.delete(id, CurrentUser.get());
    }
}
