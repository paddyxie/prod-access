package io.prodaccess.control.migration;

import io.prodaccess.common.json.Json;
import java.sql.Connection;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit, non-destructive upgrade: preserve legacy evidence before the new registry is used. */
@Component
@DependsOnDatabaseInitialization
public class SchemaUpgrade implements InitializingBean {
    private static final List<String> TABLES = List.of("tools", "tasks", "task_versions", "runs", "run_events", "tool_call_audit");
    private final JdbcClient db;
    private final DataSource source;
    private final Environment environment;
    private final TransactionTemplate tx;
    public SchemaUpgrade(JdbcClient db, DataSource source, Environment environment, TransactionTemplate tx) {
        this.db=db; this.source=source; this.environment=environment; this.tx=tx;
    }
    @Override public void afterPropertiesSet() throws Exception {
        if (db.sql("SELECT COUNT(*) FROM settings WHERE k='legacy_archive_v1'").query(Integer.class).single()>0) return;
        Set<String> existing = new HashSet<>();
        try (Connection connection=source.getConnection(); var tables=connection.getMetaData().getTables(null, connection.getSchema(), null, new String[]{"TABLE"})) {
            while(tables.next()) existing.add(tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
        }
        List<String> legacy = TABLES.stream().filter(existing::contains).toList();
        boolean populated = legacy.stream().anyMatch(table->db.sql("SELECT COUNT(*) FROM "+table).query(Integer.class).single()>0);
        if(!populated) return;
        if(!environment.getProperty("prodaccess.migrate-legacy",Boolean.class,false)) {
            throw new IllegalStateException("Legacy data detected. Back up the database, then start once with --prodaccess.migrate-legacy=true to retain it in the read-only legacy archive. Service/system mappings and API contracts must be registered explicitly. See docs/migration.md. No legacy data has been deleted.");
        }
        tx.executeWithoutResult(s->{
            for(String table:legacy) for(Map<String,Object> sourceRow:db.sql("SELECT * FROM "+table).query().listOfRows()) {
                Map<String,Object> row=new LinkedHashMap<>(); sourceRow.forEach((k,v)->row.put(k.toLowerCase(Locale.ROOT),v));
                Object execution=table.equals("runs")?row.get("id"):row.get("run_id");
                db.sql("INSERT INTO legacy_archive (source_table,source_id,execution_id,source_json,archived_at) VALUES (?,?,?,?,?)")
                        .params(table,String.valueOf(row.get("id")),execution,Json.write(row),System.currentTimeMillis()).update();
            }
            db.sql("INSERT INTO settings (k,v) VALUES ('legacy_archive_v1','complete')").update();
        });
    }
}
