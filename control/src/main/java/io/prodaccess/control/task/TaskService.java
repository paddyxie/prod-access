package io.prodaccess.control.task;

import io.prodaccess.common.json.Json;
import io.prodaccess.common.param.ParamValidator;
import io.prodaccess.control.audit.AuditService;
import io.prodaccess.control.jdbc.SqlWhere;
import io.prodaccess.control.tool.ToolService;
import io.prodaccess.control.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tasks are UI-maintained single-file JS modules; every save is an immutable version. On save the script is
 * inspected (meta + params) and analysed (the tools it calls, which must all be in the registry).
 */
@Service
public class TaskService {
    private static final String SELECT_TASK = """
            SELECT t.id, t.name, t.created_by, t.created_at, t.latest_version, v.tools_json,
                   v.created_by AS updated_by, v.created_at AS updated_at
            FROM tasks t
            JOIN task_versions v ON v.task_id = t.id AND v.ver = t.latest_version""";

    private final JdbcClient db;
    private final TransactionTemplate tx;
    private final TaskInspector inspector;
    private final ToolService tools;
    private final AuditService audit;

    TaskService(JdbcClient db, TransactionTemplate tx, TaskInspector inspector, ToolService tools, AuditService audit) {
        this.db = db;
        this.tx = tx;
        this.inspector = inspector;
        this.tools = tools;
        this.audit = audit;
    }

    public record SaveInput(String name, String script) {}

    /** Named tasks only; ad-hoc scripts are reachable through their runs. */
    public List<Task> list(String q, String role) {
        SqlWhere where = new SqlWhere().raw("t.name IS NOT NULL").contains(q, "t.name")
                .contains(role == null || role.isBlank() ? null : "\"" + role + ".", "v.tools_json");
        return db.sql(SELECT_TASK + where.sql() + " ORDER BY t.name").params(where.params()).query(TaskRow.class).list()
                .stream().map(TaskRow::toTask).toList();
    }

    public Task get(String id) {
        return db.sql(SELECT_TASK + " WHERE t.id = ?").param(id).query(TaskRow.class).optional().map(TaskRow::toTask)
                .orElseThrow(() -> ApiException.notFound("task not found"));
    }

    private record TaskRow(String id, String name, String createdBy, long createdAt, int latestVersion, String toolsJson,
                           String updatedBy, long updatedAt) {
        Task toTask() {
            Set<String> keys = TaskVersion.keys(toolsJson);
            return new Task(id, name, createdBy, createdAt, latestVersion, List.copyOf(keys), List.copyOf(TaskVersion.roles(keys)),
                    updatedBy, updatedAt);
        }
    }

    public List<TaskVersion> versions(String taskId) {
        return db.sql("SELECT * FROM task_versions WHERE task_id = ? ORDER BY ver DESC").param(taskId).query(TaskVersion.class).list();
    }

    public TaskVersion version(String taskId, Integer ver) {
        get(taskId);
        String sql = ver == null
                ? "SELECT * FROM task_versions WHERE task_id = ? ORDER BY ver DESC LIMIT 1"
                : "SELECT * FROM task_versions WHERE task_id = ? AND ver = " + ver.intValue();
        return db.sql(sql).param(taskId).query(TaskVersion.class).optional()
                .orElseThrow(() -> ApiException.notFound("version not found"));
    }

    /** Creates a task (name optional: unnamed = ad-hoc script) with its first version. */
    public TaskVersion create(SaveInput in, String actor) {
        String name = in.name() == null || in.name().isBlank() ? null : in.name().trim();
        Analysed a = analyse(in.script());
        return tx.execute(s -> {
            String id = UUID.randomUUID().toString();
            db.sql("INSERT INTO tasks (id, name, created_by, created_at, latest_version) VALUES (?,?,?,?,0)")
                    .params(id, name, actor, System.currentTimeMillis()).update();
            return insertVersion(id, 1, in.script(), a, actor);
        });
    }

    /** Saves a new version of an existing task. */
    public TaskVersion newVersion(String taskId, SaveInput in, String actor) {
        Task t = get(taskId);
        Analysed a = analyse(in.script());
        return tx.execute(s -> {
            int ver = db.sql("SELECT latest_version FROM tasks WHERE id = ? FOR UPDATE").param(taskId).query(Integer.class).single() + 1;
            return insertVersion(t.id(), ver, in.script(), a, actor);
        });
    }

    /** Gives an ad-hoc (unnamed) task a name. */
    public Task saveAs(String taskId, String name, String actor) {
        Task t = get(taskId);
        if (t.name() != null) throw ApiException.conflict("task already has a name");
        if (name == null || name.isBlank()) throw ApiException.bad("name is required");
        db.sql("UPDATE tasks SET name = ? WHERE id = ?").params(name.trim(), taskId).update();
        audit.record(actor, "task.name", taskId, name);
        return get(taskId);
    }

    private record Analysed(TaskInspector.Inspection inspection, Set<String> tools) {}

    private Analysed analyse(String script) {
        if (script == null || script.isBlank()) throw ApiException.bad("script is required");
        Set<String> refs = ToolRefAnalyzer.analyze(script);
        Set<String> unknown = new TreeSet<>(refs);
        unknown.removeAll(tools.byKey(refs).keySet());
        if (!unknown.isEmpty()) throw ApiException.bad("not in the tool registry: " + String.join(", ", unknown));

        var inspection = inspector.inspect(script);
        try {
            ParamValidator.checkDefinitions(inspection.params());
        } catch (IllegalArgumentException e) {
            throw ApiException.bad(e.getMessage());
        }
        return new Analysed(inspection, refs);
    }

    private TaskVersion insertVersion(String taskId, int ver, String script, Analysed a, String actor) {
        String id = UUID.randomUUID().toString();
        db.sql("""
                INSERT INTO task_versions (id, task_id, ver, script, script_hash, meta_json, params_json, tools_json,
                                           created_by, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)""")
                .params(id, taskId, ver, script, sha256(script), Json.write(a.inspection().meta()),
                        Json.write(a.inspection().params()), Json.write(a.tools()), actor, System.currentTimeMillis())
                .update();
        db.sql("UPDATE tasks SET latest_version = ? WHERE id = ?").params(ver, taskId).update();
        audit.record(actor, "task.version", taskId, "v" + ver + " tools=" + a.tools());
        return db.sql("SELECT * FROM task_versions WHERE id = ?").param(id).query(TaskVersion.class).single();
    }

    public static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
