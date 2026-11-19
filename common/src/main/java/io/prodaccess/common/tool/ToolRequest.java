package io.prodaccess.common.tool;

import io.prodaccess.common.json.Json;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The arguments of one tool call, as a script passes them: {@code { path, query, headers, body }}. */
public record ToolRequest(Map<String, String> path, Map<String, String> query, Map<String, String> headers, Object body) {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]+)}");

    public static ToolRequest from(Map<?, ?> args) {
        return new ToolRequest(strings(args.get("path")), strings(args.get("query")), strings(args.get("headers")), args.get("body"));
    }

    /** {@code base} + the path template with its placeholders filled + the query string. */
    public String url(String base, String pathTemplate) {
        Matcher m = PLACEHOLDER.matcher(pathTemplate);
        StringBuilder p = new StringBuilder();
        while (m.find()) {
            String v = path.get(m.group(1));
            if (v == null) throw new IllegalArgumentException("missing path parameter '" + m.group(1) + "'");
            m.appendReplacement(p, Matcher.quoteReplacement(URLEncoder.encode(v, StandardCharsets.UTF_8).replace("+", "%20")));
        }
        m.appendTail(p);
        StringBuilder u = new StringBuilder(base.replaceAll("/+$", "")).append(p);
        char sep = '?';
        for (var e : query.entrySet()) {
            u.append(sep).append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=').append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            sep = '&';
        }
        return u.toString();
    }

    /** The body as JSON text, or null when there is none. */
    public String bodyJson() {
        return body == null ? null : Json.write(body);
    }

    private static Map<String, String> strings(Object o) {
        Map<String, String> out = new LinkedHashMap<>();
        if (o instanceof Map<?, ?> m) m.forEach((k, v) -> {
            if (v != null) out.put(String.valueOf(k), String.valueOf(v));
        });
        return out;
    }
}
