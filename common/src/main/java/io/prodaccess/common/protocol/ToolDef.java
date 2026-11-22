package io.prodaccess.common.protocol;

/** Immutable API contract and endpoint binding pinned by a workflow version. */
public record ToolDef(String versionId, String service, String role, String name, ToolType type, String method,
                      String pathTemplate, String baseUrl, String inputSchemaJson, String outputSchemaJson) {
    public String key() { return role + "." + name; }
}
