package io.prodaccess.common.tool;

import io.prodaccess.common.json.Json;

public record ToolResponse(int status, String contentType, String body) {
    public boolean ok() {
        return status >= 200 && status < 300;
    }

    /** The body as JSON when it is JSON, else as text; null when empty. */
    public Object value() {
        if (body == null || body.isBlank()) return null;
        if (contentType.toLowerCase().contains("json")) {
            try {
                return Json.readTree(body);
            } catch (IllegalArgumentException ignored) {
                // fall through to text
            }
        }
        return body;
    }
}
