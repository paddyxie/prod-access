package io.prodaccess.control.run;

import static io.prodaccess.control.run.RunStatus.CANCELLED;
import static io.prodaccess.control.run.RunStatus.FAILED;
import static io.prodaccess.control.run.RunStatus.QUEUED;
import static io.prodaccess.control.run.RunStatus.RUNNING;
import static io.prodaccess.control.run.RunStatus.SUCCEEDED;

import io.prodaccess.common.json.Json;
import io.prodaccess.common.param.ParamValidator;
import io.prodaccess.common.protocol.RunEvent;
import io.prodaccess.common.tool.ToolRequest;
import io.prodaccess.common.tool.ToolResponse;
import io.prodaccess.common.tool.Tools;
import io.prodaccess.control.audit.AuditService;
import io.prodaccess.control.config.ProdAccessProperties;
import io.prodaccess.control.jdbc.SqlWhere;
import io.prodaccess.control.ownership.OwnershipService;
import io.prodaccess.control.task.TaskService;
import io.prodaccess.control.task.TaskVersion;
import io.prodaccess.control.tool.Tool;
import io.prodaccess.control.tool.ToolService;
import io.prodaccess.control.web.ApiException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Run lifecycle (see {@link RunStatus}). Every transition is a compare-and-set on the status so concurrent actors
 * and pods cannot double-apply it. A user may start a run only when their teams own every role it calls.
 */
@Service
public class RunService {
    private static final String SELECT_RUN = "SELECT r.*, t.name AS task_name FROM runs r LEFT JOIN tasks t ON t.id = r.task_id";

    private final JdbcClient db;
    private final TaskService tasks;
    private final ToolService tools;
    private final OwnershipService ownership;
    private final ProdAccessProperties props;
    private final AuditService audit;

    RunService(JdbcClient db, TaskService tasks, ToolService tools, OwnershipService ownership, ProdAccessProperties props,
               AuditService audit) {
        this.db = db;
        this.tasks = tasks;
        this.tools = tools;
        this.ownership = ownership;
        this.props = props;
        this.audit = audit;
    }

    public record Filter(RunStatus status, RunKind kind, String taskId, String submittedBy) {}

    public List<Run> list(Filter f, int limit) {
        int n = Math.min(Math.max(limit, 1), 500);
        SqlWhere where = new SqlWhere().eq("r.status", f.status() == null ? null : f.status().name())
                .eq("r.kind", f.kind() == null ? null : f.kind().name())
                .eq("r.task_id", f.taskId()).eq("r.submitted_by", f.submittedBy());
        return db.sql(SELECT_RUN + where.sql() + " ORDER BY r.created_at DESC LIMIT " + n).params(where.params())
                .query(Run.class).list();
    }

    public Run get(String id) {
        return db.sql(SELECT_RUN + " WHERE r.id = ?").param(id).query(Run.class).optional()
                .orElseThrow(() -> ApiException.notFound("run not found"));
    }

    public List<RunLogEntry> events(String runId, long after) {
        return db.sql("SELECT * FROM run_events WHERE run_id = ? AND id > ? ORDER BY id LIMIT 1000")
                .params(runId, after).query(RunLogEntry.class).list();
    }

    public List<ToolCallRecord> toolCalls(String runId) {
        return db.sql("SELECT * FROM tool_call_audit WHERE run_id = ? ORDER BY id").param(runId).query(ToolCallRecord.class).list();
    }

    /** Queues a task version; the dispatcher runs it in a child process. */
    @SuppressWarnings("unchecked")
    public Run submit(String taskId, Integer ver, Map<String, Object> params, String actor) {
        TaskVersion v = tasks.version(taskId, ver);
        Map<String, Object> coerced = ParamValidator.validate((Map<String, Object>) Json.readTree(v.paramsJson()), params);
        requireOwner(actor, v.roles(), "task " + taskId + " v" + v.ver());
        String id = UUID.randomUUID().toString();
        db.sql("""
                INSERT INTO runs (id, kind, task_id, ver, roles_json, params_json, submitted_by, status, created_at, cancel_requested)
                VALUES (?,?,?,?,?,?,?,?,?,FALSE)""")
                .params(id, RunKind.SCRIPT.name(), taskId, v.ver(), Json.write(v.roles()), Json.write(coerced), actor, QUEUED.name(),
                        System.currentTimeMillis())
                .update();
        audit.record(actor, "run.submit", id, "task=" + taskId + " v" + v.ver() + " roles=" + v.roles());
        return get(id);
    }

    /**
     * Calls one tool right away from this process and records it as a run: no script runs, so no sandbox or child
     * process is needed. {@code request} has the shape a script passes: {@code {path, query, headers, body}}.
     */
    public Run call(String toolId, Map<String, Object> request, String actor) {
        Tool tool = tools.get(toolId);
        requireOwner(actor, Set.of(tool.role()), "tool " + tool.key());
        Map<String, Object> req = request == null ? Map.of() : request;
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        db.sql("""
                INSERT INTO runs (id, kind, tool_key, roles_json, params_json, submitted_by, status, created_at, started_at, cancel_requested)
                VALUES (?,?,?,?,?,?,?,?,?,FALSE)""")
                .params(id, RunKind.CALL.name(), tool.key(), Json.write(List.of(tool.role())), Json.write(req), actor, RUNNING.name(), now, now)
                .update();
        audit.record(actor, "run.call", id, tool.key());

        long t0 = System.nanoTime();
        try {
            ToolResponse r = new Tools(props.apiForge().toSpec(), id, actor).call(tool.def(), ToolRequest.from(req));
            String err = r.ok() ? null : "HTTP " + r.status();
            recordToolCall(id, RunEvent.tool(tool.def(), r.status(), (System.nanoTime() - t0) / 1_000_000, err));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", r.status());
            result.put("body", r.value());
            finish(id, r.ok() ? SUCCEEDED : FAILED, Json.write(result), err);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finish(id, FAILED, null, "interrupted");
        } catch (Exception e) {
            String err = e.getClass().getSimpleName() + ": " + e.getMessage();
            recordToolCall(id, RunEvent.tool(tool.def(), null, (System.nanoTime() - t0) / 1_000_000, err));
            finish(id, FAILED, null, err);
        }
        return get(id);
    }

    public Run cancel(String runId, String actor) {
        Run r = get(runId);
        int n = db.sql("UPDATE runs SET status = ?, finished_at = ?, error = 'cancelled' WHERE id = ? AND status = ?")
                .params(CANCELLED.name(), System.currentTimeMillis(), runId, QUEUED.name()).update();
        if (n == 0 && (r.kind() != RunKind.SCRIPT
                || db.sql("UPDATE runs SET cancel_requested = TRUE WHERE id = ? AND status = ?").params(runId, RUNNING.name()).update() == 0)) {
            throw ApiException.conflict("run is " + r.status());
        }
        audit.record(actor, "run.cancel", runId, r.status().name());
        return get(runId);
    }

    /** Settles a RUNNING run; a no-op if something else (a sweep, a cancel) settled it first. */
    boolean finish(String runId, RunStatus status, String resultJson, String error) {
        return db.sql("UPDATE runs SET status = ?, finished_at = ?, result_json = ?, error = ? WHERE id = ? AND status = 'RUNNING'")
                .params(status.name(), System.currentTimeMillis(), resultJson, error, runId).update() > 0;
    }

    void recordToolCall(String runId, RunEvent ev) {
        db.sql("""
                INSERT INTO tool_call_audit (run_id, ts, role, tool, type, method, path, status, duration_ms, error)
                VALUES (?,?,?,?,?,?,?,?,?,?)""")
                .params(runId, ev.ts(), ev.role(), ev.tool(), ev.type().name(), ev.method(), ev.path(), ev.status(), ev.ms(),
                        ev.err() == null ? null : ev.err().substring(0, Math.min(500, ev.err().length())))
                .update();
    }

    private void requireOwner(String actor, Collection<String> roles, String target) {
        try {
            ownership.requireOwner(actor, roles);
        } catch (ApiException e) {
            audit.record(actor, "run.refused", target, e.getMessage());
            throw e;
        }
    }
}
