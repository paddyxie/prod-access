package io.prodaccess.control.tool;

import io.prodaccess.common.protocol.ToolDef;
import io.prodaccess.common.protocol.ToolType;

/** A stable tool identity projected with one immutable definition version. */
public record Tool(String id, String cmdbRoleId, String service, String role, String name, boolean retired,
                   String versionId, int ver, ToolType type, String method, String pathTemplate, String baseUrl,
                   String gatewayApiId, String description, String inputSchemaJson, String outputSchemaJson,
                   String updatedBy, long updatedAt) {
    public String key() { return role + "." + name; }
    public ToolDef def() {
        return new ToolDef(versionId, service, role, name, type, method, pathTemplate, baseUrl, inputSchemaJson, outputSchemaJson);
    }
}
