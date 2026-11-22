package io.prodaccess.common.schema;

import io.prodaccess.common.json.Json;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Explicit JSON Schema subset shared by the registry, direct calls, and runner. Unsupported assertions are rejected. */
public final class Schema {
    private static final Set<String> KEYWORDS = Set.of("type", "properties", "required", "additionalProperties", "items",
            "enum", "const", "minimum", "maximum", "minLength", "maxLength", "minItems", "maxItems", "pattern",
            "$schema", "$id", "title", "description", "default", "examples", "format", "readOnly", "writeOnly", "$comment");
    private static final Set<String> TYPES = Set.of("object", "array", "string", "number", "integer", "boolean", "null");
    private Schema() {}

    public static String definition(String json) {
        if (json == null || json.isBlank()) throw new IllegalArgumentException("input and output schemas are required");
        Object schema = Json.readTree(json);
        check(schema, 0);
        return Json.write(schema);
    }

    private static void check(Object raw, int depth) {
        if (depth > 40) throw new IllegalArgumentException("schema too deeply nested");
        if (raw instanceof Boolean) return;
        if (!(raw instanceof Map<?, ?> m)) throw new IllegalArgumentException("schema must be an object or boolean");
        for (Object key : m.keySet()) if (!KEYWORDS.contains(key.toString())) throw new IllegalArgumentException("unsupported schema keyword: " + key);
        Object type = m.get("type");
        if (type != null) {
            List<?> types = type instanceof List<?> l ? l : List.of(type);
            if (types.isEmpty() || types.stream().anyMatch(t -> !TYPES.contains(t.toString()))) throw new IllegalArgumentException("invalid schema type");
        }
        if (m.containsKey("properties")) {
            if (!(m.get("properties") instanceof Map<?, ?> p)) throw new IllegalArgumentException("properties must be an object");
            p.values().forEach(s -> check(s, depth + 1));
        }
        if (m.containsKey("required") && (!(m.get("required") instanceof List<?> l) || l.stream().anyMatch(v -> !(v instanceof String)))) {
            throw new IllegalArgumentException("required must be a string array");
        }
        for (String key : List.of("items", "additionalProperties")) if (m.containsKey(key)) check(m.get(key), depth + 1);
        if (m.containsKey("enum") && (!(m.get("enum") instanceof List<?> l) || l.isEmpty())) throw new IllegalArgumentException("enum must be nonempty");
        for (String key : List.of("minimum", "maximum", "minLength", "maxLength", "minItems", "maxItems")) {
            if (m.containsKey(key) && !(m.get(key) instanceof Number)) throw new IllegalArgumentException(key + " must be numeric");
        }
        if (m.containsKey("pattern")) Pattern.compile(m.get("pattern").toString());
    }

    public static void validate(String json, Object value, String path) {
        validateValue(Json.readTree(json), value, path);
    }

    private static void validateValue(Object raw, Object v, String path) {
        if (Boolean.TRUE.equals(raw)) return;
        if (Boolean.FALSE.equals(raw)) fail(path, "value is forbidden");
        Map<?, ?> m = (Map<?, ?>) raw;
        Object type = m.get("type");
        if (type != null) {
            List<?> types = type instanceof List<?> l ? l : List.of(type);
            if (types.stream().noneMatch(t -> matches(t.toString(), v))) fail(path, "expected " + type);
        }
        if (m.get("enum") instanceof List<?> options && options.stream().noneMatch(o -> equivalent(o, v))) fail(path, "not in enum");
        if (m.containsKey("const") && !equivalent(m.get("const"), v)) fail(path, "does not match const");
        if (v instanceof Map<?, ?> obj) {
            Map<?, ?> props = m.get("properties") instanceof Map<?, ?> p ? p : Map.of();
            if (m.get("required") instanceof List<?> required) for (Object key : required) {
                if (!obj.containsKey(key)) fail(path + "." + key, "required");
            }
            obj.forEach((key, value) -> {
                if (props.containsKey(key)) validateValue(props.get(key), value, path + "." + key);
                else if (m.containsKey("additionalProperties")) validateValue(m.get("additionalProperties"), value, path + "." + key);
            });
        }
        if (v instanceof List<?> list) {
            bounds(m, list.size(), "minItems", "maxItems", path);
            if (m.containsKey("items")) for (int i = 0; i < list.size(); i++) validateValue(m.get("items"), list.get(i), path + "[" + i + "]");
        }
        if (v instanceof String s) {
            bounds(m, s.codePointCount(0, s.length()), "minLength", "maxLength", path);
            if (m.containsKey("pattern") && !Pattern.compile(m.get("pattern").toString()).matcher(s).find()) fail(path, "pattern mismatch");
        }
        if (v instanceof Number n) bounds(m, n.doubleValue(), "minimum", "maximum", path);
    }

    private static void bounds(Map<?, ?> m, double n, String min, String max, String path) {
        if (m.get(min) instanceof Number lower && n < lower.doubleValue()) fail(path, "below " + min);
        if (m.get(max) instanceof Number upper && n > upper.doubleValue()) fail(path, "above " + max);
    }
    private static boolean equivalent(Object a, Object b) {
        return a instanceof Number x && b instanceof Number y ? Double.compare(x.doubleValue(), y.doubleValue()) == 0 : java.util.Objects.equals(a, b);
    }
    private static boolean matches(String type, Object v) {
        return switch (type) {
            case "object" -> v instanceof Map;
            case "array" -> v instanceof List;
            case "string" -> v instanceof String;
            case "number" -> v instanceof Number;
            case "integer" -> v instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue());
            case "boolean" -> v instanceof Boolean;
            case "null" -> v == null;
            default -> false;
        };
    }
    private static void fail(String path, String message) { throw new IllegalArgumentException(path + ": " + message); }
}
