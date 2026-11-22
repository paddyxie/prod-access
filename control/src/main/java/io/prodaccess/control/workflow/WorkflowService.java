package io.prodaccess.control.workflow;

import io.prodaccess.common.json.Json;
import io.prodaccess.common.param.ParamValidator;
import io.prodaccess.common.schema.Schema;
import io.prodaccess.control.audit.AuditService;
import io.prodaccess.control.tool.Tool;
import io.prodaccess.control.tool.ToolService;
import io.prodaccess.control.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** A workflow is a reusable script; versions pin both the script and all API contracts it uses. */
@Service
public class WorkflowService {
    private final JdbcClient db;
    private final TransactionTemplate tx;
    private final WorkflowInspector inspector;
    private final ToolService tools;
    private final AuditService audit;

    WorkflowService(JdbcClient db, TransactionTemplate tx, WorkflowInspector inspector, ToolService tools, AuditService audit) {
        this.db = db; this.tx = tx; this.inspector = inspector; this.tools = tools; this.audit = audit;
    }
    public record SaveInput(String name, String script) {}
    private record WorkflowRow(String id, String name, String createdBy, long createdAt, int latestVersion) {}
    private record VersionRow(String id, String workflowId, int ver, String script, String scriptHash, String metaJson,
                              String paramsJson, String outputSchemaJson, String createdBy, long createdAt) {}

    public List<Workflow> list(String q, String role) {
        return db.sql("SELECT * FROM workflows WHERE name IS NOT NULL ORDER BY name").query(WorkflowRow.class).list().stream()
                .filter(t -> q == null || q.isBlank() || t.name().toLowerCase().contains(q.toLowerCase()))
                .map(this::project).filter(t -> role == null || role.isBlank() || t.roles().contains(role)).toList();
    }
    public Workflow get(String id) {
        return project(db.sql("SELECT * FROM workflows WHERE id = ?").param(id).query(WorkflowRow.class).optional()
                .orElseThrow(() -> ApiException.notFound("workflow not found")));
    }
    private Workflow project(WorkflowRow row) {
        WorkflowVersion v = version(row.id(), row.latestVersion());
        return new Workflow(row.id(), row.name(), row.createdBy(), row.createdAt(), row.latestVersion(),
                List.copyOf(v.tools()), List.copyOf(v.roles()), v.createdBy(), v.createdAt());
    }
    private WorkflowVersion project(VersionRow row) {
        var bindings = tools.forWorkflowVersion(row.id()).stream()
                .map(t -> new WorkflowVersion.ToolBinding(t.id(), t.versionId(), t.key(), t.ver(), t.retired())).toList();
        var keys = bindings.stream().map(WorkflowVersion.ToolBinding::key).toList();
        return new WorkflowVersion(row.id(), row.workflowId(), row.ver(), row.script(), row.scriptHash(), row.metaJson(),
                row.paramsJson(), row.outputSchemaJson(), Json.write(keys), bindings, row.createdBy(), row.createdAt());
    }
    public List<WorkflowVersion> versions(String workflowId) {
        return db.sql("SELECT * FROM workflow_versions WHERE workflow_id = ? ORDER BY ver DESC").param(workflowId)
                .query(VersionRow.class).list().stream().map(this::project).toList();
    }
    public WorkflowVersion version(String workflowId, Integer ver) {
        String sql = "SELECT * FROM workflow_versions WHERE workflow_id = ?" + (ver == null ? " ORDER BY ver DESC LIMIT 1" : " AND ver = " + ver.intValue());
        return db.sql(sql).param(workflowId).query(VersionRow.class).optional().map(this::project)
                .orElseThrow(() -> ApiException.notFound("workflow version not found"));
    }
    public WorkflowVersion versionById(String id) {
        return db.sql("SELECT * FROM workflow_versions WHERE id = ?").param(id).query(VersionRow.class).optional().map(this::project)
                .orElseThrow(() -> ApiException.notFound("workflow version not found"));
    }
    public WorkflowVersion create(SaveInput in, String actor) {
        Analysed a = analyse(in.script());
        return tx.execute(s -> {
            String id = UUID.randomUUID().toString();
            String name = in.name() == null || in.name().isBlank() ? null : in.name().trim();
            db.sql("INSERT INTO workflows (id,name,created_by,created_at,latest_version) VALUES (?,?,?,?,0)")
                    .params(id, name, actor, System.currentTimeMillis()).update();
            return insertVersion(id, 1, in.script(), a, actor);
        });
    }
    public WorkflowVersion newVersion(String workflowId, SaveInput in, String actor) {
        get(workflowId);
        Analysed a = analyse(in.script());
        return tx.execute(s -> {
            int ver = db.sql("SELECT latest_version FROM workflows WHERE id = ? FOR UPDATE").param(workflowId).query(Integer.class).single() + 1;
            return insertVersion(workflowId, ver, in.script(), a, actor);
        });
    }
    public Workflow saveAs(String workflowId, String name, String actor) {
        Workflow t = get(workflowId);
        if (t.name() != null) throw ApiException.conflict("workflow already has a name");
        if (name == null || name.isBlank()) throw ApiException.bad("name is required");
        db.sql("UPDATE workflows SET name = ? WHERE id = ?").params(name.trim(), workflowId).update();
        audit.record(actor, "workflow.name", workflowId, name);
        return get(workflowId);
    }
    private record Analysed(WorkflowInspector.Inspection inspection, List<Tool> tools) {}
    private Analysed analyse(String script) {
        if (script == null || script.isBlank()) throw ApiException.bad("script is required");
        Set<String> refs = ToolRefAnalyzer.analyze(script);
        Map<String, Tool> registered = tools.byKey(refs);
        Set<String> unknown = new TreeSet<>(refs); unknown.removeAll(registered.keySet());
        if (!unknown.isEmpty()) throw ApiException.bad("not in the tool registry: " + String.join(", ", unknown));
        var inspection = inspector.inspect(script);
        try { ParamValidator.checkDefinitions(inspection.params()); Schema.definition(Json.write(inspection.output())); }
        catch (IllegalArgumentException e) { throw ApiException.bad(e.getMessage()); }
        return new Analysed(inspection, List.copyOf(registered.values()));
    }
    private WorkflowVersion insertVersion(String workflowId, int ver, String script, Analysed a, String actor) {
        String id = UUID.randomUUID().toString();
        db.sql("""
            INSERT INTO workflow_versions (id,workflow_id,ver,script,script_hash,meta_json,params_json,output_schema_json,created_by,created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?)""")
            .params(id, workflowId, ver, script, sha256(script), Json.write(a.inspection().meta()), Json.write(a.inspection().params()),
                    Json.write(a.inspection().output()), actor, System.currentTimeMillis()).update();
        for (Tool t : a.tools()) {
            db.sql("INSERT INTO workflow_version_tools (workflow_version_id,tool_version_id,tool_id) VALUES (?,?,?)").params(id, t.versionId(), t.id()).update();
        }
        db.sql("UPDATE workflows SET latest_version = ? WHERE id = ?").params(ver, workflowId).update();
        audit.record(actor, "workflow.version", id, "v" + ver);
        return versionById(id);
    }
    public static String sha256(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
