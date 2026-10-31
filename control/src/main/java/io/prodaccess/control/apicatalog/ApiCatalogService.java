package io.prodaccess.control.apicatalog;

import io.prodaccess.common.protocol.Access;
import io.prodaccess.control.audit.AuditService;
import io.prodaccess.control.jdbc.SqlWhere;
import io.prodaccess.control.web.ApiException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class ApiCatalogService {
    private static final Set<String> METHODS = Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE");

    private final JdbcClient db;
    private final AuditService audit;

    ApiCatalogService(JdbcClient db, AuditService audit) {
        this.db = db;
        this.audit = audit;
    }

    public record ApiInput(String service, String operation, String method, String pathTemplate, String access) {}

    public List<ApiEntry> list(String q, String access) {
        SqlWhere where = new SqlWhere().contains(q, "service", "operation", "path_template").eq("access", access);
        return db.sql("SELECT * FROM api_defs" + where.sql() + " ORDER BY service, operation").params(where.params())
                .query(ApiEntry.class).list();
    }

    public List<ApiEntry> all() {
        return list(null, null);
    }

    /**
     * Keys ("service.operation") of the operations classified WRITE right now. This list is the only source of
     * read/write: callers look it up when they need it and never keep a copy.
     */
    public Set<String> writeOps() {
        return list(null, Access.WRITE.name()).stream().map(ApiEntry::key).collect(Collectors.toSet());
    }

    public ApiEntry get(String id) {
        return db.sql("SELECT * FROM api_defs WHERE id = ?").param(id).query(ApiEntry.class).optional()
                .orElseThrow(() -> ApiException.notFound("api not found"));
    }

    public ApiEntry upsert(ApiInput in, String actor) {
        require(in.service(), "service");
        require(in.operation(), "operation");
        require(in.pathTemplate(), "pathTemplate");
        String method = in.method() == null ? "" : in.method().toUpperCase();
        if (!METHODS.contains(method)) throw ApiException.bad("method must be one of " + METHODS);
        Access access = in.access() == null || in.access().isBlank() ? Access.defaultFor(method) : parseAccess(in.access());
        long now = System.currentTimeMillis();
        int n = db.sql("UPDATE api_defs SET method=?, path_template=?, access=?, updated_by=?, updated_at=? WHERE service=? AND operation=?")
                .params(method, in.pathTemplate(), access.name(), actor, now, in.service(), in.operation()).update();
        if (n == 0) {
            db.sql("INSERT INTO api_defs (id, service, operation, method, path_template, access, updated_by, updated_at) VALUES (?,?,?,?,?,?,?,?)")
                    .params(UUID.randomUUID().toString(), in.service(), in.operation(), method, in.pathTemplate(),
                            access.name(), actor, now).update();
        }
        // The read/write classification gates approval, so every override is audited with the default it overrides.
        audit.record(actor, "api.upsert", in.service() + "." + in.operation(),
                method + " " + in.pathTemplate() + " access=" + access + " (default " + Access.defaultFor(method) + ")");
        return db.sql("SELECT * FROM api_defs WHERE service=? AND operation=?").params(in.service(), in.operation())
                .query(ApiEntry.class).single();
    }

    public void delete(String id, String actor) {
        int n = db.sql("DELETE FROM api_defs WHERE id=?").param(id).update();
        if (n == 0) throw ApiException.notFound("api not found");
        audit.record(actor, "api.delete", id, null);
    }

    private static Access parseAccess(String s) {
        try {
            return Access.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.bad("access must be READ or WRITE");
        }
    }

    private static void require(String v, String field) {
        if (v == null || v.isBlank()) throw ApiException.bad(field + " is required");
    }
}
