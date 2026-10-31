package io.prodaccess.common.param;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParamValidatorTest {
    @Test
    void coercesAndAppliesDefaults() {
        var defs = Map.<String, Object>of(
                "ids", Map.of("type", "list", "item", Map.of("type", "string"), "max", 3),
                "n", Map.of("type", "number", "min", 1),
                "dry", Map.of("type", "boolean", "default", true));
        var out = ParamValidator.validate(defs, Map.of("ids", "a, b\nc", "n", "5"));
        assertEquals(List.of("a", "b", "c"), out.get("ids"));
        assertEquals(5L, out.get("n"));
        assertEquals(true, out.get("dry"));
    }

    @Test
    void reportsAllErrors() {
        var defs = Map.<String, Object>of(
                "ids", Map.of("type", "list", "item", Map.of("type", "string"), "max", 1),
                "n", Map.of("type", "number"));
        var ex = assertThrows(ParamException.class,
                () -> ParamValidator.validate(defs, Map.of("ids", "a,b", "extra", 1)));
        assertEquals(java.util.Set.of("ids", "n", "extra"), ex.errors().keySet());
    }

    @Test
    void parsesCsvWithRequiredColumns() {
        var defs = Map.<String, Object>of("rows", Map.of("type", "csv", "columns", List.of("id", "note")));
        var out = ParamValidator.validate(defs, Map.of("rows", "id,note\n1,\"hello, \"\"x\"\"\"\n2,y\n"));
        @SuppressWarnings("unchecked") var rows = (List<Map<String, String>>) out.get("rows");
        assertEquals("hello, \"x\"", rows.get(0).get("note"));
        assertEquals(2, rows.size());
        assertThrows(ParamException.class, () -> ParamValidator.validate(defs, Map.of("rows", "id\n1")));
    }
}
