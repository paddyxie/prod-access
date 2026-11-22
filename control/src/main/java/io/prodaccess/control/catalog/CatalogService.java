package io.prodaccess.control.catalog;

import io.prodaccess.control.audit.AuditService;
import io.prodaccess.control.ownership.OwnershipService;
import io.prodaccess.control.web.ApiException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][\\w$]*");
    private static final Set<String> SYSTEM_TYPES = Set.of("WEB", "KAFKA", "BATCH", "WORKER", "OTHER");
    private final JdbcClient db;
    private final OwnershipService ownership;
    private final AuditService audit;

    CatalogService(JdbcClient db, OwnershipService ownership, AuditService audit, io.prodaccess.control.migration.SchemaUpgrade upgrade) {
        this.db = db;
        this.ownership = ownership;
        this.audit = audit;
    }

    public record ServiceInput(String code, String name, String description) {}
    public record RoleInput(String serviceId, String code, String name, String systemType, String description) {}

    public List<ServiceDefinition> services() {
        return db.sql("SELECT * FROM services ORDER BY code").query(ServiceDefinition.class).list();
    }

    public List<CmdbRole> roles() {
        return db.sql("SELECT r.*, s.code AS service_code FROM cmdb_roles r JOIN services s ON s.id = r.service_id ORDER BY s.code, r.code")
                .query(CmdbRole.class).list();
    }

    public CmdbRole role(String code) {
        return roles().stream().filter(r -> r.code().equals(code)).findFirst()
                .orElseThrow(() -> ApiException.bad("register the CMDB role under its service first: " + code));
    }

    public ServiceDefinition createService(ServiceInput in, String actor) {
        required(in.code(), "code");
        required(in.name(), "name");
        // The current authorization seam grants catalog administration to users owning at least one CMDB role.
        if (ownership.ownedRoles(actor).isEmpty()) throw ApiException.forbidden("a CMDB role owner is required to register a service");
        String id = UUID.randomUUID().toString();
        db.sql("INSERT INTO services (id,code,name,description,created_by,created_at) VALUES (?,?,?,?,?,?)")
                .params(id, in.code(), in.name(), in.description(), actor, System.currentTimeMillis()).update();
        audit.record(actor, "service.create", id, in.code());
        return services().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    public CmdbRole createRole(RoleInput in, String actor) {
        required(in.code(), "code");
        required(in.name(), "name");
        if (!IDENTIFIER.matcher(in.code()).matches()) throw ApiException.bad("CMDB role code must be a JS identifier");
        if (!SYSTEM_TYPES.contains(in.systemType() == null ? "" : in.systemType())) throw ApiException.bad("invalid system type");
        if (services().stream().noneMatch(s -> s.id().equals(in.serviceId()))) throw ApiException.bad("service not found");
        ownership.requireOwner(actor, List.of(in.code()));
        String id = UUID.randomUUID().toString();
        db.sql("INSERT INTO cmdb_roles (id,service_id,code,name,system_type,description,created_by,created_at) VALUES (?,?,?,?,?,?,?,?)")
                .params(id, in.serviceId(), in.code(), in.name(), in.systemType(), in.description(), actor, System.currentTimeMillis()).update();
        audit.record(actor, "cmdb_role.create", id, in.code());
        return role(in.code());
    }

    private static void required(String value, String field) {
        if (value == null || value.isBlank()) throw ApiException.bad(field + " is required");
    }
}
