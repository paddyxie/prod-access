package io.prodaccess.control.tool;

import io.prodaccess.common.protocol.ToolType;
import io.prodaccess.control.audit.AuditService;
import io.prodaccess.control.jdbc.SqlWhere;
import io.prodaccess.control.ownership.OwnershipService;
import io.prodaccess.control.web.ApiException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** The tool registry. Teams that own a role maintain its tools; every change is audited. */
@Service
public class ToolService {
    private static final Set<String> METHODS = Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][\\w$]*");

    private final JdbcClient db;
    private final OwnershipService ownership;
    private final AuditService audit;

    ToolService(JdbcClient db, OwnershipService ownership, AuditService audit) {
        this.db = db;
        this.ownership = ownership;
        this.audit = audit;
    }

    public record ToolInput(String role, String name, ToolType type, String method, String pathTemplate, String baseUrl,
                            String description) {}

    public List<Tool> list(String q, String role, ToolType type) {
        SqlWhere where = new SqlWhere().contains(q, "role", "name", "path_template", "description").eq("role", role)
                .eq("type", type == null ? null : type.name());
        return db.sql("SELECT * FROM tools" + where.sql() + " ORDER BY role, name").params(where.params()).query(Tool.class).list();
    }

    public Tool get(String id) {
        return db.sql("SELECT * FROM tools WHERE id = ?").param(id).query(Tool.class).optional()
                .orElseThrow(() -> ApiException.notFound("tool not found"));
    }

    /** The registered tools among these keys ({@code role.name}), by key; unknown keys are absent. */
    public Map<String, Tool> byKey(Collection<String> keys) {
        return list(null, null, null).stream().filter(t -> keys.contains(t.key()))
                .collect(Collectors.toMap(Tool::key, Function.identity()));
    }

    public Tool create(ToolInput in, String actor) {
        validate(in);
        ownership.requireOwner(actor, List.of(in.role()));
        if (db.sql("SELECT COUNT(*) FROM tools WHERE role = ? AND name = ?").params(in.role(), in.name()).query(Integer.class).single() > 0) {
            throw ApiException.conflict("tool " + in.role() + "." + in.name() + " already exists");
        }
        String id = UUID.randomUUID().toString();
        db.sql("""
                INSERT INTO tools (id, role, name, type, method, path_template, base_url, description, updated_by, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?)""")
                .params(id, in.role(), in.name(), in.type().name(), in.method().toUpperCase(), in.pathTemplate(), baseUrl(in),
                        blankToNull(in.description()), actor, System.currentTimeMillis())
                .update();
        audit.record(actor, "tool.create", in.role() + "." + in.name(), summary(in));
        return get(id);
    }

    /** Role and name are the tool's identity in scripts, so they cannot change; delete and re-create instead. */
    public Tool update(String id, ToolInput in, String actor) {
        Tool t = get(id);
        if (!t.role().equals(in.role()) || !t.name().equals(in.name())) throw ApiException.bad("role and name cannot change");
        validate(in);
        ownership.requireOwner(actor, List.of(t.role()));
        db.sql("UPDATE tools SET type=?, method=?, path_template=?, base_url=?, description=?, updated_by=?, updated_at=? WHERE id=?")
                .params(in.type().name(), in.method().toUpperCase(), in.pathTemplate(), baseUrl(in), blankToNull(in.description()),
                        actor, System.currentTimeMillis(), id)
                .update();
        audit.record(actor, "tool.update", t.key(), summary(in));
        return get(id);
    }

    public void delete(String id, String actor) {
        Tool t = get(id);
        ownership.requireOwner(actor, List.of(t.role()));
        db.sql("DELETE FROM tools WHERE id = ?").param(id).update();
        audit.record(actor, "tool.delete", t.key(), null);
    }

    private static void validate(ToolInput in) {
        if (in.role() == null || !IDENTIFIER.matcher(in.role()).matches()) throw ApiException.bad("role must be a JS identifier");
        if (in.name() == null || !IDENTIFIER.matcher(in.name()).matches()) throw ApiException.bad("name must be a JS identifier");
        if (in.type() == null) throw ApiException.bad("type is required");
        if (in.method() == null || !METHODS.contains(in.method().toUpperCase())) throw ApiException.bad("method must be one of " + METHODS);
        if (in.pathTemplate() == null || !in.pathTemplate().startsWith("/")) throw ApiException.bad("pathTemplate must start with /");
        if (in.type() == ToolType.HTTP_CLIENT
                && (in.baseUrl() == null || !(in.baseUrl().startsWith("http://") || in.baseUrl().startsWith("https://")))) {
            throw ApiException.bad("an HTTP Client tool needs a baseUrl starting with http:// or https://");
        }
    }

    private static String baseUrl(ToolInput in) {
        return in.type() == ToolType.HTTP_CLIENT ? in.baseUrl().replaceAll("/+$", "") : null;
    }

    private static String summary(ToolInput in) {
        return in.type() + " " + in.method().toUpperCase() + " " + (in.type() == ToolType.HTTP_CLIENT ? baseUrl(in) : "") + in.pathTemplate();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
