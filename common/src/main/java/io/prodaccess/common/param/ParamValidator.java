package io.prodaccess.common.param;

import io.prodaccess.common.json.Json;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validates and coerces submitted values against the param descriptors a task script declares
 * (the plain objects produced by {@code param.string(...)} and friends).
 */
public final class ParamValidator {
    private static final int MAX_LIST_DEFAULT = 10_000;

    private static final java.util.Set<String> TYPES = java.util.Set.of(
            "string", "text", "number", "boolean", "date", "datetime", "enum", "json", "list", "csv");

    private ParamValidator() {}

    /** Sanity-checks the descriptors a script declares, so a bad definition fails at save time, not at run time. */
    @SuppressWarnings("unchecked")
    public static void checkDefinitions(Map<String, Object> defs) {
        for (var e : defs.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?> raw)) throw new IllegalArgumentException("param '" + e.getKey() + "' must use param.<type>(...)");
            Map<String, Object> d = (Map<String, Object>) raw;
            Object type = d.get("type");
            if (!TYPES.contains(String.valueOf(type))) throw new IllegalArgumentException("param '" + e.getKey() + "': unknown type " + type);
            if (type.equals("enum") && !(d.get("options") instanceof List<?> l && !l.isEmpty())) {
                throw new IllegalArgumentException("param '" + e.getKey() + "': enum needs options");
            }
            if (type.equals("list")) {
                if (!(d.get("item") instanceof Map<?, ?>)) throw new IllegalArgumentException("param '" + e.getKey() + "': list needs an item type");
                checkDefinitions(Map.of(e.getKey() + "[]", d.get("item")));
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> validate(Map<String, Object> defs, Map<String, Object> values) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> in = values == null ? Map.of() : values;
        for (var e : defs.entrySet()) {
            String name = e.getKey();
            Map<String, Object> d = (Map<String, Object>) e.getValue();
            Object v = in.get(name);
            boolean blank = v == null || (v instanceof String s && s.isBlank());
            if (blank) {
                if (d.containsKey("default") && d.get("default") != null) {
                    out.put(name, d.get("default"));
                } else if (!Boolean.FALSE.equals(d.get("required"))) {
                    errors.put(name, "required");
                } else {
                    out.put(name, null);
                }
                continue;
            }
            try {
                out.put(name, coerce(d, v));
            } catch (IllegalArgumentException ex) {
                errors.put(name, ex.getMessage());
            }
        }
        for (String k : in.keySet()) {
            if (!defs.containsKey(k)) errors.put(k, "unknown param");
        }
        if (!errors.isEmpty()) throw new ParamException(errors);
        return out;
    }

    @SuppressWarnings("unchecked")
    static Object coerce(Map<String, Object> d, Object v) {
        String type = String.valueOf(d.get("type"));
        switch (type) {
            case "string", "text" -> {
                String s = v.toString();
                Number max = (Number) d.get("maxLength");
                if (max != null && s.length() > max.intValue()) throw new IllegalArgumentException("longer than " + max);
                Object pat = d.get("pattern");
                if (pat != null && !Pattern.compile(pat.toString()).matcher(s).matches()) {
                    throw new IllegalArgumentException("does not match " + pat);
                }
                return s;
            }
            case "number" -> {
                double n;
                try {
                    n = v instanceof Number num ? num.doubleValue() : Double.parseDouble(v.toString().trim());
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("not a number");
                }
                if (d.get("min") instanceof Number min && n < min.doubleValue()) throw new IllegalArgumentException("less than " + min);
                if (d.get("max") instanceof Number max && n > max.doubleValue()) throw new IllegalArgumentException("greater than " + max);
                return n == Math.rint(n) && Math.abs(n) < 1e15 ? (Object) (long) n : (Object) n;
            }
            case "boolean" -> {
                if (v instanceof Boolean b) return b;
                String s = v.toString().trim();
                if (s.equalsIgnoreCase("true")) return true;
                if (s.equalsIgnoreCase("false")) return false;
                throw new IllegalArgumentException("not a boolean");
            }
            case "date" -> {
                try {
                    return LocalDate.parse(v.toString().trim()).toString();
                } catch (DateTimeParseException ex) {
                    throw new IllegalArgumentException("expected yyyy-MM-dd");
                }
            }
            case "datetime" -> {
                String s = v.toString().trim();
                try {
                    return OffsetDateTime.parse(s).toString();
                } catch (DateTimeParseException ex) {
                    try {
                        return LocalDateTime.parse(s).toString();
                    } catch (DateTimeParseException ex2) {
                        throw new IllegalArgumentException("expected ISO-8601 date-time");
                    }
                }
            }
            case "enum" -> {
                List<Object> options = (List<Object>) d.get("options");
                String s = v.toString();
                for (Object o : options) if (String.valueOf(o).equals(s)) return o;
                throw new IllegalArgumentException("must be one of " + options);
            }
            case "json" -> {
                if (v instanceof String s) {
                    try {
                        return Json.readTree(s);
                    } catch (IllegalArgumentException ex) {
                        throw new IllegalArgumentException("invalid JSON");
                    }
                }
                return v;
            }
            case "list" -> {
                Map<String, Object> item = (Map<String, Object>) d.get("item");
                List<?> raw;
                if (v instanceof List<?> l) {
                    raw = l;
                } else {
                    List<String> parts = new ArrayList<>();
                    for (String p : v.toString().split("[\\r\\n,]+")) if (!p.isBlank()) parts.add(p.trim());
                    raw = parts;
                }
                int max = d.get("max") instanceof Number m ? m.intValue() : MAX_LIST_DEFAULT;
                if (raw.size() > max) throw new IllegalArgumentException("more than " + max + " items");
                List<Object> out = new ArrayList<>();
                for (int i = 0; i < raw.size(); i++) {
                    try {
                        out.add(coerce(item, raw.get(i)));
                    } catch (IllegalArgumentException ex) {
                        throw new IllegalArgumentException("item " + (i + 1) + ": " + ex.getMessage());
                    }
                }
                return out;
            }
            case "csv" -> {
                List<Map<String, String>> rows = Csv.parse(v.toString());
                int max = d.get("max") instanceof Number m ? m.intValue() : MAX_LIST_DEFAULT;
                if (rows.size() > max) throw new IllegalArgumentException("more than " + max + " rows");
                List<String> columns = (List<String>) d.get("columns");
                if (columns != null && !rows.isEmpty()) {
                    for (String c : columns) {
                        if (!rows.get(0).containsKey(c)) throw new IllegalArgumentException("missing column " + c);
                    }
                }
                return rows;
            }
            default -> throw new IllegalArgumentException("unsupported param type " + type);
        }
    }
}
