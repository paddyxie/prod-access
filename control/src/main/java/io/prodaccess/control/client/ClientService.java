package io.prodaccess.control.client;

import io.prodaccess.control.audit.AuditService;
import io.prodaccess.control.jdbc.SqlWhere;
import io.prodaccess.control.web.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class ClientService {
    private final JdbcClient db;
    private final AuditService audit;

    ClientService(JdbcClient db, AuditService audit) {
        this.db = db;
        this.audit = audit;
    }

    public record ClientInput(String name, String description, String environment, String credentialRef, Boolean enabled) {}

    public List<Client> list(String q) {
        SqlWhere where = new SqlWhere().contains(q, "name", "description", "credential_ref");
        return db.sql("SELECT * FROM clients" + where.sql() + " ORDER BY name").params(where.params()).query(Client.class).list();
    }

    public Client get(String id) {
        return db.sql("SELECT * FROM clients WHERE id = ?").param(id).query(Client.class).optional()
                .orElseThrow(() -> ApiException.notFound("client not found"));
    }

    public Client create(ClientInput in, String actor) {
        require(in.name(), "name");
        require(in.credentialRef(), "credentialRef");
        String id = UUID.randomUUID().toString();
        db.sql("INSERT INTO clients (id, name, description, environment, credential_ref, enabled, created_by, created_at) VALUES (?,?,?,?,?,?,?,?)")
                .params(id, in.name().trim(), in.description(), in.environment() == null || in.environment().isBlank() ? "PROD" : in.environment(),
                        in.credentialRef().trim(), in.enabled() == null || in.enabled(), actor, System.currentTimeMillis())
                .update();
        audit.record(actor, "client.create", id, in.name());
        return get(id);
    }

    public Client update(String id, ClientInput in, String actor) {
        Client c = get(id);
        db.sql("UPDATE clients SET description=?, environment=?, credential_ref=?, enabled=? WHERE id=?")
                .params(in.description() != null ? in.description() : c.description(),
                        in.environment() != null ? in.environment() : c.environment(),
                        in.credentialRef() != null ? in.credentialRef() : c.credentialRef(),
                        in.enabled() != null ? in.enabled() : c.enabled(), id)
                .update();
        audit.record(actor, "client.update", id, c.name() + (in.enabled() != null ? " enabled=" + in.enabled() : ""));
        return get(id);
    }

    private static void require(String v, String field) {
        if (v == null || v.isBlank()) throw ApiException.bad(field + " is required");
    }
}
