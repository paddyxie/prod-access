package io.prodaccess.control.run;

import io.prodaccess.common.protocol.RunEvent;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Receives ordered lifecycle evidence. Start records are committed before the runner is acknowledged. */
@Service
public class ExecutionAudit {
    private final JdbcClient db;
    private final TransactionTemplate tx;

    ExecutionAudit(JdbcClient db, TransactionTemplate tx) {
        this.db = db;
        this.tx = tx;
    }

    private static final String SELECT_INVOCATIONS = """
            SELECT i.*, s.code AS service, r.code AS role, t.name AS tool, v.ver, v.type
            FROM tool_invocations i JOIN tool_versions v ON v.id = i.tool_version_id
            JOIN tool_definitions t ON t.id = v.tool_id JOIN cmdb_roles r ON r.id = t.cmdb_role_id
            JOIN services s ON s.id = r.service_id""";
    public List<ToolCallRecord> invocations(String executionId) {
        return db.sql(SELECT_INVOCATIONS + " WHERE i.execution_id = ? ORDER BY i.started_at,i.id")
                .param(executionId).query(ToolCallRecord.class).list();
    }
    public List<ToolCallRecord> search(String actor, String service, String role, String tool, String status, Long from, Long to) {
        var where = new io.prodaccess.control.jdbc.SqlWhere().eq("i.actor", actor).eq("s.code", service).eq("r.code", role).eq("t.name", tool).eq("i.status", status);
        if (from != null) where.raw("i.started_at >= " + from.longValue());
        if (to != null) where.raw("i.started_at <= " + to.longValue());
        return db.sql(SELECT_INVOCATIONS + where.sql() + " ORDER BY i.started_at DESC,i.id LIMIT 500").params(where.params()).query(ToolCallRecord.class).list();
    }
    public List<ApiCallRecord> apiCalls(String executionId) {
        return db.sql("SELECT a.* FROM api_calls a JOIN tool_invocations i ON i.id = a.invocation_id WHERE i.execution_id = ? ORDER BY a.started_at,a.attempt")
                .param(executionId).query(ApiCallRecord.class).list();
    }
    public List<ExecutionAction> actions(String executionId) {
        return db.sql("SELECT * FROM execution_actions WHERE execution_id = ? ORDER BY started_at,id").param(executionId).query(ExecutionAction.class).list();
    }
    public boolean record(String executionId, RunEvent event) {
        if (!(event.value() instanceof Map<?, ?> data)) return false;
        return switch (event.t()) {
            case "action_start", "invocation_start", "http_start" -> Boolean.TRUE.equals(tx.execute(transaction -> {
                // Serialize the start with terminal transitions, which lock the same execution row.
                String status = db.sql("SELECT status FROM executions WHERE id = ? FOR UPDATE").param(executionId)
                        .query(String.class).single();
                if (!"RUNNING".equals(status)) throw new IllegalStateException("execution no longer accepts new actions or calls");
                return write(executionId, event, data);
            }));
            default -> write(executionId, event, data);
        };
    }

    private boolean write(String executionId, RunEvent event, Map<?, ?> data) {
        long ts = event.ts();
        switch (event.t()) {
            case "invocation_start" -> db.sql("""
                INSERT INTO tool_invocations (id,execution_id,action_id,tool_version_id,actor,started_at,status,input_json,input_truncated)
                VALUES (?,?,?,?,?,?,'RUNNING',?,?)""")
                .params(data.get("id"), executionId, data.get("actionId"), data.get("toolVersionId"), data.get("actor"), ts,
                        data.get("inputJson"), data.get("inputTruncated")).update();
            case "invocation_end" -> db.sql("""
                UPDATE tool_invocations SET finished_at=?,status=?,output_json=?,output_truncated=?,error=? WHERE id=? AND execution_id=? AND status='RUNNING'""")
                .params(ts, data.get("status"), data.get("outputJson"), Boolean.TRUE.equals(data.get("outputTruncated")),
                        data.get("error"), data.get("id"), executionId).update();
            case "http_start" -> db.sql("""
                INSERT INTO api_calls (id,invocation_id,attempt,started_at,method,url,request_headers_json,request_body,request_truncated,status)
                VALUES (?,?,?,?,?,?,?,?,?,'RUNNING')""")
                .params(data.get("id"), data.get("invocationId"), data.get("attempt"), ts, data.get("method"), data.get("url"),
                        data.get("requestHeadersJson"), data.get("requestBody"), data.get("requestTruncated")).update();
            case "http_end" -> db.sql("""
                UPDATE api_calls SET finished_at=?,response_status=?,response_headers_json=?,response_body=?,response_truncated=?,status=?,error=?
                WHERE id=? AND status='RUNNING'""")
                .params(ts, data.get("responseStatus"), data.get("responseHeadersJson"), data.get("responseBody"),
                        Boolean.TRUE.equals(data.get("responseTruncated")), data.get("status"), data.get("error"), data.get("id")).update();
            case "action_start" -> db.sql("""
                INSERT INTO execution_actions (id,execution_id,parent_action_id,name,status,started_at,input_json) VALUES (?,?,?,?,'RUNNING',?,?)""")
                .params(data.get("id"), executionId, data.get("parentId"), data.get("name"), ts, data.get("inputJson")).update();
            case "action_end" -> db.sql("UPDATE execution_actions SET status=?,finished_at=?,result_json=?,error=? WHERE id=? AND execution_id=? AND status='RUNNING'")
                .params(data.get("status"), ts, data.get("resultJson"), data.get("error"), data.get("id"), executionId).update();
            default -> { return false; }
        }
        return true;
    }
    public void closeIncomplete(String executionId, String reason) {
        long ts = System.currentTimeMillis();
        db.sql("UPDATE api_calls SET status='UNKNOWN',finished_at=?,error=? WHERE status='RUNNING' AND invocation_id IN (SELECT id FROM tool_invocations WHERE execution_id=?)")
                .params(ts, reason, executionId).update();
        db.sql("UPDATE tool_invocations SET status='UNKNOWN',finished_at=?,error=? WHERE execution_id=? AND status='RUNNING'").params(ts, reason, executionId).update();
        db.sql("UPDATE execution_actions SET status='UNKNOWN',finished_at=?,error=? WHERE execution_id=? AND status='RUNNING'").params(ts, reason, executionId).update();
    }
}
