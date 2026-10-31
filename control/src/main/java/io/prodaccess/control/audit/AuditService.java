package io.prodaccess.control.audit;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Audit trail of UI operations (who changed/ran/approved what). Run and API-call audit live with the run. */
@Service
public class AuditService {
    private final JdbcClient db;

    AuditService(JdbcClient db) {
        this.db = db;
    }

    public void record(String actor, String action, String target, String detail) {
        db.sql("INSERT INTO audit_log (ts, actor, action, target, detail) VALUES (?,?,?,?,?)")
                .params(System.currentTimeMillis(), actor, action, target, detail).update();
    }
}
