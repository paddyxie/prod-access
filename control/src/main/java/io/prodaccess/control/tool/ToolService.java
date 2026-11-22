package io.prodaccess.control.tool;

import io.prodaccess.common.protocol.ToolType;
import io.prodaccess.common.schema.Schema;
import io.prodaccess.control.audit.AuditService;
import io.prodaccess.control.catalog.CatalogService;
import io.prodaccess.control.jdbc.SqlWhere;
import io.prodaccess.control.ownership.OwnershipService;
import io.prodaccess.control.web.ApiException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ToolService {
    private static final String SELECT = """
            SELECT t.id, t.cmdb_role_id, s.code AS service, r.code AS role, t.name, t.retired,
              v.id AS version_id, v.ver, v.type, v.method, v.path_template, v.base_url, v.gateway_api_id,
              v.description, v.input_schema_json, v.output_schema_json, v.created_by AS updated_by, v.created_at AS updated_at
            FROM tool_definitions t JOIN cmdb_roles r ON r.id = t.cmdb_role_id
            JOIN services s ON s.id = r.service_id JOIN tool_versions v ON v.tool_id = t.id""";
    private static final String LATEST = "v.ver = (SELECT MAX(v2.ver) FROM tool_versions v2 WHERE v2.tool_id = t.id)";
    private static final Set<String> METHODS = Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][\\w$]*");
    private final JdbcClient db;
    private final OwnershipService ownership;
    private final AuditService audit;
    private final CatalogService catalog;
    private final TransactionTemplate tx;

    ToolService(JdbcClient db, OwnershipService ownership, AuditService audit, CatalogService catalog, TransactionTemplate tx) {
        this.db = db; this.ownership = ownership; this.audit = audit; this.catalog = catalog; this.tx = tx;
    }
    public record ToolInput(String role, String name, ToolType type, String method, String pathTemplate, String baseUrl,
                            String gatewayApiId, String description, String inputSchemaJson, String outputSchemaJson) {}

    public List<Tool> list(String q, String role, ToolType type) {
        SqlWhere w = new SqlWhere().raw("t.retired = FALSE").raw(LATEST)
                .contains(q, "r.code", "t.name", "v.path_template", "v.description", "s.code").eq("r.code", role)
                .eq("v.type", type == null ? null : type.name());
        return db.sql(SELECT + w.sql() + " ORDER BY s.code, r.code, t.name").params(w.params()).query(Tool.class).list();
    }
    public Tool get(String id) {
        return db.sql(SELECT + " WHERE t.id = ? AND " + LATEST).param(id).query(Tool.class).optional()
                .orElseThrow(() -> ApiException.notFound("tool not found"));
    }
    public Tool version(String versionId) {
        return db.sql(SELECT + " WHERE v.id = ?").param(versionId).query(Tool.class).optional()
                .orElseThrow(() -> ApiException.notFound("tool version not found"));
    }
    public List<Tool> versions(String id) {
        get(id);
        return db.sql(SELECT + " WHERE t.id = ? ORDER BY v.ver DESC").param(id).query(Tool.class).list();
    }
    public Map<String, Tool> byKey(Collection<String> keys) {
        return list(null, null, null).stream().filter(t -> keys.contains(t.key())).collect(Collectors.toMap(Tool::key, Function.identity()));
    }
    public List<Tool> forWorkflowVersion(String versionId) {
        return db.sql(SELECT + " JOIN workflow_version_tools w ON w.tool_version_id = v.id WHERE w.workflow_version_id = ? ORDER BY r.code,t.name")
                .param(versionId).query(Tool.class).list();
    }
    public Tool create(ToolInput in, String actor) {
        validate(in);
        ownership.requireOwner(actor, List.of(in.role()));
        var role = catalog.role(in.role());
        return tx.execute(s -> {
            String id = UUID.randomUUID().toString();
            db.sql("INSERT INTO tool_definitions (id,cmdb_role_id,name,retired,created_by,created_at) VALUES (?,?,?,FALSE,?,?)")
                    .params(id, role.id(), in.name(), actor, System.currentTimeMillis()).update();
            insertVersion(id, 1, in, actor);
            audit.record(actor, "tool.create", id, in.role() + "." + in.name() + " v1");
            return get(id);
        });
    }
    public Tool update(String id, ToolInput in, String actor) {
        validate(in);
        return tx.execute(s -> {
            db.sql("SELECT id FROM tool_definitions WHERE id = ? FOR UPDATE").param(id).query(String.class).single();
            Tool t = get(id);
            if (t.retired()) throw ApiException.conflict("tool is retired");
            if (!t.role().equals(in.role()) || !t.name().equals(in.name())) throw ApiException.bad("role and name cannot change");
            ownership.requireOwner(actor, List.of(t.role()));
            insertVersion(id, t.ver() + 1, in, actor);
            audit.record(actor, "tool.version", id, "v" + (t.ver() + 1));
            return get(id);
        });
    }
    public void delete(String id, String actor) {
        Tool t = get(id);
        ownership.requireOwner(actor, List.of(t.role()));
        db.sql("UPDATE tool_definitions SET retired = TRUE WHERE id = ?").param(id).update();
        audit.record(actor, "tool.retire", id, t.key());
    }
    private void insertVersion(String id, int ver, ToolInput in, String actor) {
        db.sql("""
            INSERT INTO tool_versions (id,tool_id,ver,type,method,path_template,base_url,gateway_api_id,description,input_schema_json,output_schema_json,created_by,created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""")
            .params(UUID.randomUUID().toString(), id, ver, in.type().name(), in.method().toUpperCase(), in.pathTemplate(),
                    in.type() == ToolType.HTTP_CLIENT ? in.baseUrl().replaceAll("/+$", "") : null,
                    in.type() == ToolType.API_FORGE ? in.gatewayApiId() : null, in.description(),
                    Schema.definition(in.inputSchemaJson()), Schema.definition(in.outputSchemaJson()), actor, System.currentTimeMillis()).update();
    }
    private static void validate(ToolInput in) {
        if (in.role() == null || !IDENTIFIER.matcher(in.role()).matches() || in.name() == null || !IDENTIFIER.matcher(in.name()).matches()) throw ApiException.bad("role and name must be JS identifiers");
        if (in.type() == null) throw ApiException.bad("type is required");
        if (in.method() == null || !METHODS.contains(in.method().toUpperCase())) throw ApiException.bad("invalid HTTP method");
        if (in.pathTemplate() == null || !in.pathTemplate().startsWith("/")) throw ApiException.bad("pathTemplate must start with /");
        if (in.type() == ToolType.HTTP_CLIENT && (in.baseUrl() == null || !(in.baseUrl().startsWith("http://") || in.baseUrl().startsWith("https://")))) throw ApiException.bad("HTTP Client requires an http(s) baseUrl");
        try { Schema.definition(in.inputSchemaJson()); Schema.definition(in.outputSchemaJson()); }
        catch (IllegalArgumentException e) { throw ApiException.bad(e.getMessage()); }
    }
}
