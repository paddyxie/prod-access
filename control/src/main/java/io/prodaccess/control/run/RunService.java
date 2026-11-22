package io.prodaccess.control.run;

import io.prodaccess.common.json.Json;
import io.prodaccess.common.param.ParamValidator;
import io.prodaccess.common.tool.AuditCapture;
import io.prodaccess.common.tool.Tools;
import io.prodaccess.control.audit.AuditService;
import io.prodaccess.control.config.ProdAccessProperties;
import io.prodaccess.control.jdbc.SqlWhere;
import io.prodaccess.control.ownership.OwnershipService;
import io.prodaccess.control.tool.Tool;
import io.prodaccess.control.tool.ToolService;
import io.prodaccess.control.web.ApiException;
import io.prodaccess.control.workflow.WorkflowService;
import io.prodaccess.control.workflow.WorkflowVersion;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Execution lifecycle; definitions and evidence remain separate and version references never change. */
@Service
public class RunService {
    private static final String SELECT_RUN = """
            SELECT e.*, v.workflow_id, w.name AS workflow_name, v.ver, r.code || '.' || t.name AS tool_key,
                   i.resolved_json AS params_json, o.value_json AS result_json
            FROM executions e LEFT JOIN workflow_versions v ON v.id = e.workflow_version_id
            LEFT JOIN workflows w ON w.id = v.workflow_id LEFT JOIN tool_versions tv ON tv.id = e.tool_version_id
            LEFT JOIN tool_definitions t ON t.id = tv.tool_id LEFT JOIN cmdb_roles r ON r.id = t.cmdb_role_id
            JOIN execution_inputs i ON i.execution_id = e.id LEFT JOIN execution_outputs o ON o.execution_id = e.id""";

    private final JdbcClient db;
    private final WorkflowService workflows;
    private final ToolService tools;
    private final OwnershipService ownership;
    private final ProdAccessProperties props;
    private final AuditService audit;
    private final ExecutionAudit evidence;
    private final TransactionTemplate tx;

    RunService(JdbcClient db, WorkflowService workflows, ToolService tools, OwnershipService ownership, ProdAccessProperties props,
               AuditService audit, ExecutionAudit evidence, TransactionTemplate tx) {
        this.db = db;
        this.workflows = workflows;
        this.tools = tools;
        this.ownership = ownership;
        this.props = props;
        this.audit = audit;
        this.evidence = evidence;
        this.tx = tx;
    }

    public record Filter(RunStatus status, RunKind kind, String workflowId, String submittedBy) {}
    public record ExecutionInput(String executionId, String submittedJson, String resolvedJson) {}

    public List<Run> list(Filter filter, int limit) {
        SqlWhere where = new SqlWhere().eq("e.status", filter.status() == null ? null : filter.status().name())
                .eq("e.kind", filter.kind() == null ? null : filter.kind().name())
                .eq("v.workflow_id", filter.workflowId()).eq("e.submitted_by", filter.submittedBy());
        int count = Math.min(Math.max(limit, 1), 500);
        return db.sql(SELECT_RUN + where.sql() + " ORDER BY e.created_at DESC LIMIT " + count)
                .params(where.params()).query(Run.class).list();
    }

    public Run get(String id) {
        return db.sql(SELECT_RUN + " WHERE e.id = ?").param(id).query(Run.class).optional()
                .orElseThrow(() -> ApiException.notFound("execution not found"));
    }

    public ExecutionInput input(String id) {
        get(id);
        return db.sql("SELECT * FROM execution_inputs WHERE execution_id = ?").param(id).query(ExecutionInput.class).single();
    }

    public List<RunLogEntry> events(String id, long after) {
        return db.sql("SELECT id, execution_id AS run_id, ts, kind, lvl, message FROM execution_events WHERE execution_id = ? AND id > ? ORDER BY id LIMIT 1000")
                .params(id, after).query(RunLogEntry.class).list();
    }

    public List<ToolCallRecord> toolCalls(String id) {
        get(id);
        return evidence.invocations(id);
    }

    @SuppressWarnings("unchecked")
    public Run submit(String workflowId, Integer ver, Map<String, Object> params, String actor) {
        WorkflowVersion version = workflows.version(workflowId, ver);
        Map<String, Object> resolved = ParamValidator.validate((Map<String, Object>) Json.readTree(version.paramsJson()), params);
        requireOwner(actor, version.roles(), "workflow " + workflowId + " v" + version.ver());
        for (Tool tool : tools.forWorkflowVersion(version.id())) {
            if (tool.retired()) throw ApiException.conflict("tool is retired: " + tool.key());
        }
        return create(RunKind.SCRIPT, version.id(), null, version.roles(), params, resolved, actor, RunStatus.QUEUED);
    }

    private Run create(RunKind kind, String workflowVersionId, String toolVersionId, Collection<String> roles,
                       Object submitted, Object resolved, String actor, RunStatus status) {
        return tx.execute(s -> {
            String id = UUID.randomUUID().toString();
            long now = System.currentTimeMillis();
            db.sql("""
                    INSERT INTO executions (id, kind, workflow_version_id, tool_version_id, roles_json, submitted_by,
                                            status, created_at, started_at, heartbeat_at, cancel_requested)
                    VALUES (?,?,?,?,?,?,?,?,?,?,FALSE)""")
                    .params(id, kind.name(), workflowVersionId, toolVersionId, Json.write(roles), actor, status.name(), now,
                            status == RunStatus.RUNNING ? now : null, status == RunStatus.RUNNING ? now : null).update();
            db.sql("INSERT INTO execution_inputs (execution_id, submitted_json, resolved_json) VALUES (?,?,?)")
                    .params(id, Json.write(submitted), Json.write(resolved)).update();
            audit.record(actor, "execution.submit", id, kind.name());
            return get(id);
        });
    }

    public Run call(String toolId, String toolVersionId, Map<String, Object> request, String actor) {
        Tool tool = toolVersionId == null ? tools.get(toolId) : tools.version(toolVersionId);
        if (!tool.id().equals(toolId)) throw ApiException.bad("tool version does not belong to this tool");
        if (tool.retired()) throw ApiException.conflict("tool is retired");
        requireOwner(actor, Set.of(tool.role()), "tool " + tool.key());
        Map<String, Object> args = request == null ? Map.of() : request;
        Object auditInput = AuditCapture.redact(args);
        Run execution = create(RunKind.CALL, null, tool.versionId(), Set.of(tool.role()), auditInput, auditInput, actor, RunStatus.RUNNING);
        String id = execution.id();
        Thread heartbeat = directHeartbeat(id);
        try {
            var result = new Tools(props.apiForge().toSpec(), id, actor, event -> evidence.record(id, event), () -> null).call(tool.def(), args);
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("status", result.status());
            value.put("body", AuditCapture.redact(result.value()));
            finish(id, result.ok() ? RunStatus.SUCCEEDED : RunStatus.FAILED, Json.write(value), result.ok() ? null : "HTTP " + result.status());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finish(id, RunStatus.FAILED, null, "interrupted");
        } catch (Exception e) {
            finish(id, RunStatus.FAILED, null, e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            heartbeat.interrupt();
        }
        return get(id);
    }

    private Thread directHeartbeat(String id) {
        return Thread.ofVirtual().name("direct-heartbeat-" + id).start(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(3000);
                    db.sql("UPDATE executions SET heartbeat_at = ? WHERE id = ? AND status = 'RUNNING'")
                            .params(System.currentTimeMillis(), id).update();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public Run cancel(String id, String actor) {
        Run execution = get(id);
        int count = db.sql("UPDATE executions SET status = 'CANCELLED', finished_at = ?, error = 'cancelled' WHERE id = ? AND status = 'QUEUED'")
                .params(System.currentTimeMillis(), id).update();
        if (count == 0 && (execution.kind() != RunKind.SCRIPT
                || db.sql("UPDATE executions SET cancel_requested = TRUE WHERE id = ? AND status = 'RUNNING'").param(id).update() == 0)) {
            throw ApiException.conflict("execution is " + execution.status());
        }
        audit.record(actor, "execution.cancel", id, execution.status().name());
        return get(id);
    }

    boolean finish(String id, RunStatus status, String result, String error) {
        return Boolean.TRUE.equals(tx.execute(s -> {
            int count = db.sql("UPDATE executions SET status = ?, finished_at = ?, error = ? WHERE id = ? AND status = 'RUNNING'")
                    .params(status.name(), System.currentTimeMillis(), error, id).update();
            if (count == 0) return false;
            if (result != null) {
                db.sql("INSERT INTO execution_outputs (execution_id, value_json, recorded_at) VALUES (?,?,?)")
                        .params(id, result, System.currentTimeMillis()).update();
            }
            evidence.closeIncomplete(id, "execution ended as " + status + "; remote outcome is unknown");
            return true;
        }));
    }

    private void requireOwner(String actor, Collection<String> roles, String target) {
        try {
            ownership.requireOwner(actor, roles);
        } catch (ApiException e) {
            audit.record(actor, "execution.refused", target, e.getMessage());
            throw e;
        }
    }
}
