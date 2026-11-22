package io.prodaccess.control.migration;

import io.prodaccess.control.web.ApiException;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/legacy")
class LegacyController {
    private final JdbcClient db;
    LegacyController(JdbcClient db, SchemaUpgrade upgrade) { this.db=db; }
    public record LegacyRecord(String sourceTable,String sourceId,String executionId,String sourceJson,long archivedAt) {}
    @GetMapping List<LegacyRecord> list(@RequestParam(required=false) String executionId, @RequestParam(defaultValue="runs") String sourceTable) {
        if(executionId!=null && !executionId.isBlank()) return db.sql("SELECT * FROM legacy_archive WHERE execution_id=? ORDER BY source_table,source_id")
                .param(executionId).query(LegacyRecord.class).list();
        return db.sql("SELECT * FROM legacy_archive WHERE source_table=? ORDER BY archived_at DESC,source_id LIMIT 500").param(sourceTable).query(LegacyRecord.class).list();
    }
    @GetMapping("/{table}/{id}") LegacyRecord get(@PathVariable String table,@PathVariable String id) {
        return db.sql("SELECT * FROM legacy_archive WHERE source_table=? AND source_id=?").params(table,id).query(LegacyRecord.class).optional()
                .orElseThrow(()->ApiException.notFound("legacy record not found"));
    }
}
