package io.prodaccess.control.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import io.prodaccess.common.protocol.RunEvent;
import io.prodaccess.control.config.ProdAccessProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

class ExecutionAuditTest {
    @Test
    void lostTransitionAndIncompleteEvidenceCloseTogetherAndRejectNewCalls() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:execution-audit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(source);
        JdbcClient db = JdbcClient.create(source);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        var audit = new ExecutionAudit(db, tx);
        var config = new ProdAccessProperties("", null, null, new ProdAccessProperties.Runner("", "", 256, 1, 5000, 20_000, 30_000, false), null);
        db.sql("INSERT INTO services (id,code,name,created_by,created_at) VALUES ('service','orders','Orders','alice',0)").update();
        db.sql("INSERT INTO cmdb_roles (id,service_id,code,name,system_type,created_by,created_at) VALUES ('role','service','orders','Orders','WEB','alice',0)").update();
        db.sql("INSERT INTO tool_definitions (id,cmdb_role_id,name,created_by,created_at) VALUES ('tool','role','lookup','alice',0)").update();
        db.sql("""
                INSERT INTO tool_versions (id,tool_id,ver,type,method,path_template,input_schema_json,output_schema_json,created_by,created_at)
                VALUES ('version','tool',1,'API_FORGE','GET','/lookup','{}','{}','alice',0)""").update();
        db.sql("""
                INSERT INTO executions (id,kind,tool_version_id,roles_json,submitted_by,status,created_at,heartbeat_at,cancel_requested)
                VALUES (?,'CALL','version','[]','alice','RUNNING',0,?,false)""").params("stale", 0).update();
        db.sql("""
                INSERT INTO executions (id,kind,tool_version_id,roles_json,submitted_by,status,created_at,heartbeat_at,cancel_requested)
                VALUES (?,'CALL','version','[]','alice','RUNNING',0,?,false)""").params("fresh", System.currentTimeMillis()).update();
        audit.record("stale", RunEvent.withValue("action_start", Map.of("id", "action", "name", "Lookup", "inputJson", "{}")));
        audit.record("stale", RunEvent.withValue("invocation_start", Map.of("id", "invocation", "actor", "alice", "actionId", "action",
                "toolVersionId", "version", "inputJson", "{}", "inputTruncated", false)));
        audit.record("stale", RunEvent.withValue("http_start", Map.of("id", "request", "invocationId", "invocation", "attempt", 1,
                "method", "GET", "url", "/lookup", "requestHeadersJson", "{}", "requestTruncated", false)));

        var failingAudit = spy(audit);
        doThrow(new IllegalStateException("audit unavailable")).when(failingAudit).closeIncomplete(anyString(), anyString());
        var failingDispatcher = new RunDispatcher(db, tx, config, null, null, null, null, null, failingAudit);
        assertThrows(IllegalStateException.class, failingDispatcher::sweepLost);
        assertEquals("RUNNING", db.sql("SELECT status FROM executions WHERE id = 'stale'").query(String.class).single());

        new RunDispatcher(db, tx, config, null, null, null, null, null, audit).sweepLost();
        assertEquals("LOST", db.sql("SELECT status FROM executions WHERE id = 'stale'").query(String.class).single());
        assertEquals("RUNNING", db.sql("SELECT status FROM executions WHERE id = 'fresh'").query(String.class).single());
        assertEquals("UNKNOWN", audit.actions("stale").getFirst().status());
        assertEquals("UNKNOWN", audit.invocations("stale").getFirst().status());
        assertEquals("UNKNOWN", audit.apiCalls("stale").getFirst().status());

        var lateRequest = RunEvent.withValue("http_start", Map.of("id", "late", "invocationId", "invocation", "attempt", 2,
                "method", "GET", "url", "/lookup", "requestHeadersJson", "{}", "requestTruncated", false));
        assertThrows(IllegalStateException.class, () -> audit.record("stale", lateRequest));
        assertEquals(1, audit.apiCalls("stale").size());
    }
}
