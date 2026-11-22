package io.prodaccess.control.migration;

import static org.junit.jupiter.api.Assertions.*;
import io.prodaccess.common.json.Json;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;

class SchemaUpgradeTest {
    @Test void upgradeRequiresAnExplicitSwitchAndPreservesOriginalEvidenceWithoutInventingContracts() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        new ResourceDatabasePopulator(new ClassPathResource("legacy-schema.sql"),new ClassPathResource("schema.sql")).execute(source);
        JdbcClient db=JdbcClient.create(source);
        db.sql("INSERT INTO tools VALUES ('tool1','orders','getOrder','API_FORGE','GET','/orders/{id}',NULL,'legacy','alice',100)").update();
        db.sql("INSERT INTO tasks VALUES ('task1','repair','alice',100,1)").update();
        db.sql("INSERT INTO task_versions VALUES ('version1','task1',1,'export default function main(){return 1;}',?, '{}','{}','[\"orders.getOrder\"]','alice',100)")
                .param("0".repeat(64)).update();
        db.sql("INSERT INTO runs (id,kind,task_id,ver,roles_json,params_json,submitted_by,status,created_at,cancel_requested,result_json) VALUES ('run1','SCRIPT','task1',1,'[\"orders\"]','{}','bob','SUCCEEDED',200,FALSE,'1')").update();
        db.sql("INSERT INTO run_events (run_id,ts,kind,message) VALUES ('run1',210,'log','done')").update();
        db.sql("INSERT INTO tool_call_audit (run_id,ts,role,tool,type,method,path,status) VALUES ('run1',205,'orders','getOrder','API_FORGE','GET','/orders/{id}',200)").update();
        var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        var env=new MockEnvironment();
        var upgrade=new SchemaUpgrade(db,source,env,tx);
        assertThrows(IllegalStateException.class,upgrade::afterPropertiesSet);
        assertEquals(0,db.sql("SELECT COUNT(*) FROM legacy_archive").query(Integer.class).single());
        env.setProperty("prodaccess.migrate-legacy","true");
        upgrade.afterPropertiesSet();
        assertEquals(6,db.sql("SELECT COUNT(*) FROM legacy_archive").query(Integer.class).single());
        String original=db.sql("SELECT source_json FROM legacy_archive WHERE source_table='runs'").query(String.class).single();
        assertEquals("bob",((java.util.Map<?,?>)Json.readTree(original)).get("submitted_by"));
        assertEquals(3,db.sql("SELECT COUNT(*) FROM legacy_archive WHERE execution_id='run1'").query(Integer.class).single());
        assertEquals(1,db.sql("SELECT COUNT(*) FROM runs").query(Integer.class).single());
        assertEquals(0,db.sql("SELECT COUNT(*) FROM services").query(Integer.class).single());
        assertEquals(0,db.sql("SELECT COUNT(*) FROM executions").query(Integer.class).single());
        upgrade.afterPropertiesSet();
        assertEquals(6,db.sql("SELECT COUNT(*) FROM legacy_archive").query(Integer.class).single());
    }
}
