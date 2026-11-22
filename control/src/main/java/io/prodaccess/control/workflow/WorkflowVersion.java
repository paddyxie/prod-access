package io.prodaccess.control.workflow;

import io.prodaccess.common.json.Json;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** An immutable saved version of a workflow's script, with what was derived from the script itself at save time. */
public record WorkflowVersion(String id, String workflowId, int ver, String script, String scriptHash, String metaJson,
                              String paramsJson, String outputSchemaJson, String toolsJson, List<ToolBinding> toolBindings,
                              String createdBy, long createdAt) {

    public record ToolBinding(String toolId, String versionId, String key, int ver, boolean retired) {}

    /** Keys ({@code role.name}) of the tools the script calls. */
    public Set<String> tools() {
        return keys(toolsJson);
    }

    /** The provider roles of those tools: who may run this version is decided by them. */
    public Set<String> roles() {
        return roles(tools());
    }

    static Set<String> keys(String toolsJson) {
        Set<String> keys = new TreeSet<>();
        for (Object o : (List<?>) Json.readTree(toolsJson)) keys.add(String.valueOf(o));
        return keys;
    }

    static Set<String> roles(Set<String> toolKeys) {
        Set<String> roles = new TreeSet<>();
        for (String k : toolKeys) roles.add(k.substring(0, k.indexOf('.')));
        return roles;
    }
}
