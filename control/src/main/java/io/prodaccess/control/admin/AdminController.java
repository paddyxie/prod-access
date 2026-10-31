package io.prodaccess.control.admin;

import io.prodaccess.control.audit.AuditService;
import io.prodaccess.control.run.RunDispatcher;
import io.prodaccess.control.security.CurrentUser;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
class AdminController {
    private final RunDispatcher dispatcher;
    private final AuditService audit;

    AdminController(RunDispatcher dispatcher, AuditService audit) {
        this.dispatcher = dispatcher;
        this.audit = audit;
    }

    @GetMapping("/status")
    Map<String, Object> status() {
        return Map.of("paused", dispatcher.paused(), "activeRuns", dispatcher.activeRuns());
    }

    /** Global kill switch: stops dispatching new runs (running ones continue; cancel them individually). */
    @PostMapping("/pause")
    Map<String, Object> pause(@RequestBody Map<String, Boolean> body) {
        boolean paused = Boolean.TRUE.equals(body.get("paused"));
        dispatcher.setPaused(paused);
        audit.record(CurrentUser.get(), paused ? "admin.pause" : "admin.resume", null, null);
        return status();
    }
}
