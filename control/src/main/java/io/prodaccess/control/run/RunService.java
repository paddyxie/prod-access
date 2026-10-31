package io.prodaccess.control.run;

import static io.prodaccess.control.run.RunStatus.CANCELLED;
import static io.prodaccess.control.run.RunStatus.PENDING_APPROVAL;
import static io.prodaccess.control.run.RunStatus.QUEUED;
import static io.prodaccess.control.run.RunStatus.REJECTED;
import static io.prodaccess.control.run.RunStatus.RUNNING;

import io.prodaccess.common.json.Json;
import io.prodaccess.common.param.ParamValidator;
import io.prodaccess.control.apicatalog.ApiCatalogService;
import io.prodaccess.control.audit.AuditService;
import io.prodaccess.control.client.Client;
import io.prodaccess.control.client.ClientService;
import io.prodaccess.control.jdbc.SqlWhere;
import io.prodaccess.control.task.TaskService;
import io.prodaccess.control.task.TaskVersion;
import io.prodaccess.control.web.ApiException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Run lifecycle (see {@link RunStatus}). Every transition is a compare-and-set on the status so concurrent actors
 * and pods cannot double-apply it.
 */
@Service
public class RunService {
    private static final String SELECT_RUN = """
            SELECT r.*, t.name AS task_name, c.name AS client_name
            FROM runs r JOIN tasks t ON t.id = r.task_id JOIN clients c ON c.id = r.client_id""";

    private final JdbcClient db;
    private final TaskService tasks;
    private final ClientService clients;
    private final ApiCatalogService catalog;
    private final AuditService audit;

    RunService(JdbcClient db, TaskService tasks, ClientService clients, ApiCatalogService catalog, AuditService audit) {
        this.db = db;
        this.tasks = tasks;
        this.clients = clients;
        this.catalog = catalog;
        this.audit = audit;
    }

    public record Filter(RunStatus status, String taskId, String clientId, String submittedBy) {}

    public List<Run> list(Filter f, int limit) {
        int n = Math.min(Math.max(limit, 1), 500);
        SqlWhere where = new SqlWhere().eq("r.status", f.status() == null ? null : f.status().name())
                .eq("r.task_id", f.taskId()).eq("r.client_id", f.clientId()).eq("r.submitted_by", f.submittedBy());
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

    public List<ApiCallRecord> apiCalls(String runId) {
        return db.sql("SELECT * FROM api_call_audit WHERE run_id = ? ORDER BY id").param(runId).query(ApiCallRecord.class).list();
    }

    @SuppressWarnings("unchecked")
    public Run submit(String taskId, Integer ver, Map<String, Object> params, String confirmClient, String actor) {
        TaskVersion v = tasks.version(taskId, ver);
        Client client = clients.get(v.clientId());
        if (!client.enabled()) throw ApiException.conflict("client '" + client.name() + "' is disabled");

        Map<String, Object> coerced = ParamValidator.validate((Map<String, Object>) Json.readTree(v.paramsJson()), params);
        // Decided once, from the API list as it is now, and kept with the run: a later reclassification never
        // lifts the approval requirement from a run that already has it.
        boolean writes = !Collections.disjoint(v.ops(), catalog.writeOps());
        if (writes && !client.name().equals(confirmClient)) {
            throw ApiException.bad("this task writes data as client '" + client.name() + "' (" + client.environment()
                    + "); type the client name to confirm");
        }
        String id = UUID.randomUUID().toString();
        RunStatus status = writes ? PENDING_APPROVAL : QUEUED;
        db.sql("""
                INSERT INTO runs (id, task_id, ver, client_id, params_json, submitted_by, status, approval_hash,
                                  requires_approval, created_at, cancel_requested) VALUES (?,?,?,?,?,?,?,?,?,?,FALSE)""")
                .params(id, taskId, v.ver(), v.clientId(), Json.write(coerced), actor, status.name(),
                        approvalHash(v.scriptHash(), coerced, v.clientId(), v.ops()), writes, System.currentTimeMillis())
                .update();
        audit.record(actor, "run.submit", id, "task=" + taskId + " v" + v.ver() + " status=" + status);
        return get(id);
    }

    public Run approve(String runId, String actor) {
        Run r = get(runId);
        if (r.status() != PENDING_APPROVAL) throw ApiException.conflict("run is " + r.status());
        if (r.submittedBy().equals(actor)) throw ApiException.forbidden("a run must be approved by someone other than its submitter");
        TaskVersion v = tasks.version(r.taskId(), r.ver());
        if (!approvalHash(v, r).equals(r.approvalHash())) throw ApiException.conflict("run content changed since submission");
        if (db.sql("UPDATE runs SET status = ?, approved_by = ? WHERE id = ? AND status = ?")
                .params(QUEUED.name(), actor, runId, PENDING_APPROVAL.name()).update() == 0) {
            throw ApiException.conflict("run is no longer pending approval");
        }
        audit.record(actor, "run.approve", runId, "hash=" + r.approvalHash());
        return get(runId);
    }

    public Run reject(String runId, String reason, String actor) {
        Run r = get(runId);
        if (db.sql("UPDATE runs SET status = ?, finished_at = ?, error = ? WHERE id = ? AND status = ?")
                .params(REJECTED.name(), System.currentTimeMillis(), "rejected by " + actor + (reason == null ? "" : ": " + reason),
                        runId, PENDING_APPROVAL.name()).update() == 0) {
            throw ApiException.conflict("run is " + r.status());
        }
        audit.record(actor, "run.reject", runId, reason);
        return get(runId);
    }

    public Run cancel(String runId, String actor) {
        Run r = get(runId);
        long now = System.currentTimeMillis();
        int n = db.sql("UPDATE runs SET status = ?, finished_at = ?, error = 'cancelled' WHERE id = ? AND status IN (?, ?)")
                .params(CANCELLED.name(), now, runId, PENDING_APPROVAL.name(), QUEUED.name()).update();
        if (n == 0) {
            if (db.sql("UPDATE runs SET cancel_requested = TRUE WHERE id = ? AND status = ?").params(runId, RUNNING.name()).update() == 0) {
                throw ApiException.conflict("run is " + r.status());
            }
        }
        audit.record(actor, "run.cancel", runId, r.status().name());
        return get(runId);
    }

    // ---- approval binding

    /** What an approver actually approves: the code version, the exact inputs, the Client identity and the operation set. */
    static String approvalHash(String scriptHash, Map<String, Object> params, String clientId, Set<String> ops) {
        return TaskService.sha256(Json.writeCanonical(Map.of("script", scriptHash, "params", params, "client", clientId, "ops", ops)));
    }

    @SuppressWarnings("unchecked")
    static String approvalHash(TaskVersion v, Run r) {
        return approvalHash(v.scriptHash(), (Map<String, Object>) Json.readTree(r.paramsJson()), r.clientId(), v.ops());
    }
}
