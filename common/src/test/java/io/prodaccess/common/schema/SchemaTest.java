package io.prodaccess.common.schema;

import static org.junit.jupiter.api.Assertions.*;
import io.prodaccess.common.tool.AuditCapture;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaTest {
    @Test void nestedContractsRejectMissingAndIncorrectValues() {
        String schema = Schema.definition("""
            {"type":"object","required":["body"],"additionalProperties":false,
             "properties":{"body":{"type":"array","items":{"type":"object","required":["id"],
               "properties":{"id":{"type":"integer","minimum":1}}}}}}
            """);
        assertDoesNotThrow(() -> Schema.validate(schema, Map.of("body", List.of(Map.of("id", 2))), "request"));
        assertThrows(IllegalArgumentException.class, () -> Schema.validate(schema, Map.of(), "request"));
        assertThrows(IllegalArgumentException.class, () -> Schema.validate(schema, Map.of("body", List.of(Map.of("id", "2"))), "request"));
        assertThrows(IllegalArgumentException.class, () -> Schema.validate(schema, Map.of("body", List.of(), "extra", 1), "request"));
        assertThrows(IllegalArgumentException.class, () -> Schema.definition("{\"$ref\":\"https://example.com/schema\"}"));
    }
    @Test void boundedEvidenceIsValidJsonAndCredentialsAreRedacted() {
        var copy = AuditCapture.json(Map.of("headers", Map.of("Authorization", "Bearer private"), "body", Map.of("password", "secret")));
        assertFalse(copy.text().contains("private")); assertFalse(copy.text().contains("secret"));
        var truncated = AuditCapture.json(Map.of("value", "x".repeat(200_000)));
        assertTrue(truncated.truncated());
        assertDoesNotThrow(() -> io.prodaccess.common.json.Json.readTree(truncated.text()));
        assertEquals("http://example.com/?token=[REDACTED]&id=1", AuditCapture.url("http://example.com/?token=private&id=1"));
    }
}
