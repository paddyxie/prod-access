package io.prodaccess.control.tool;

import io.prodaccess.common.protocol.ToolDef;
import io.prodaccess.common.protocol.ToolType;

/** One registered tool: an API of a provider CMDB role that scripts may call as {@code api.<role>.<name>}. */
public record Tool(String id, String role, String name, ToolType type, String method, String pathTemplate, String baseUrl,
                   String description, String updatedBy, long updatedAt) {

    public String key() {
        return role + "." + name;
    }

    public ToolDef def() {
        return new ToolDef(role, name, type, method, pathTemplate, baseUrl);
    }
}
