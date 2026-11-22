package io.prodaccess.control.run;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import io.prodaccess.control.web.ApiException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit/tool-invocations")
class AuditController {
    private final ExecutionAudit evidence;
    AuditController(ExecutionAudit evidence) { this.evidence=evidence; }
    @GetMapping List<ToolCallRecord> search(@RequestParam(required=false) String actor,@RequestParam(required=false) String service,
            @RequestParam(required=false) String role,@RequestParam(required=false) String tool,@RequestParam(required=false) String status,
            @RequestParam(required=false) String from,@RequestParam(required=false) String to) {
        return evidence.search(actor,service,role,tool,status,parse(from),parse(to));
    }
    private static Long parse(String time) {
        if(time==null || time.isBlank()) return null;
        try { return Instant.parse(time).toEpochMilli(); }
        catch(DateTimeParseException e) { throw ApiException.bad("time filters must be ISO-8601 instants"); }
    }
}
