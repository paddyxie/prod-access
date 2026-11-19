package io.prodaccess.common.protocol;

/**
 * One callable tool as the runner sees it: scripts call it as {@code api.<role>.<name>(args)}.
 * {@code baseUrl} is set only for {@link ToolType#HTTP_CLIENT} tools.
 */
public record ToolDef(String role, String name, ToolType type, String method, String pathTemplate, String baseUrl) {
    public String key() {
        return role + "." + name;
    }
}
