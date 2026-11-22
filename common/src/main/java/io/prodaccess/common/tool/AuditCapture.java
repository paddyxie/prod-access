package io.prodaccess.common.tool;

import io.prodaccess.common.json.Json;
import java.util.*;

/** Bounded audit copies; credential headers and common secret properties never enter the event stream. */
public final class AuditCapture {
    public static final int MAX_CHARS = 128 * 1024;
    private static final Set<String> SECRETS = Set.of("authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key",
            "password", "client_secret", "access_token", "refresh_token", "token", "api_key", "apikey");
    public record Copy(String text, boolean truncated) {}
    private AuditCapture() {}
    public static Object redact(Object value) {
        if (value instanceof Map<?, ?> m) {
            Map<String,Object> out = new LinkedHashMap<>();
            m.forEach((k,v) -> out.put(k.toString(), SECRETS.contains(k.toString().toLowerCase(Locale.ROOT)) ? "[REDACTED]" : redact(v)));
            return out;
        }
        if (value instanceof List<?> l) return l.stream().map(AuditCapture::redact).toList();
        return value;
    }
    public static Copy json(Object value) {
        String text = Json.write(redact(value));
        if (text.length() <= MAX_CHARS) return new Copy(text, false);
        return new Copy(Json.write(Map.of("truncated", true, "preview", text.substring(0, MAX_CHARS))), true);
    }
    public static Copy body(String body) {
        if (body == null) return new Copy(null, false);
        try { return json(Json.readTree(body)); }
        catch (IllegalArgumentException e) { return new Copy(body.substring(0, Math.min(MAX_CHARS, body.length())), body.length() > MAX_CHARS); }
    }
    public static String url(String url) {
        return url.replaceAll("(?i)([?&](?:token|access_token|api_key|apikey|password)=)[^&]*", "$1[REDACTED]");
    }
}
