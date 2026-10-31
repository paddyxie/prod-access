package io.prodaccess.control.jdbc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the WHERE clause of a list query from optional filters; blank filters are skipped. */
public final class SqlWhere {
    private final List<String> clauses = new ArrayList<>();
    private final List<Object> params = new ArrayList<>();

    public SqlWhere eq(String column, Object value) {
        if (value != null && !(value instanceof String s && s.isBlank())) {
            clauses.add(column + " = ?");
            params.add(value);
        }
        return this;
    }

    /** Case-insensitive substring match on any of the columns. */
    public SqlWhere contains(String text, String... columns) {
        if (text == null || text.isBlank()) return this;
        String pattern = "%" + text.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        List<String> any = new ArrayList<>();
        for (String c : columns) {
            any.add("LOWER(" + c + ") LIKE ?");
            params.add(pattern);
        }
        clauses.add("(" + String.join(" OR ", any) + ")");
        return this;
    }

    public SqlWhere raw(String clause) {
        clauses.add(clause);
        return this;
    }

    public String sql() {
        return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
    }

    public List<Object> params() {
        return params;
    }
}
