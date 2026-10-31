package io.prodaccess.control.task;

import io.prodaccess.common.json.Json;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * An immutable saved version of a task's script, with what was derived from the script itself at save time.
 * Whether it writes is not stored: that depends on the API list, which can change, so it is always looked up live.
 */
public record TaskVersion(String id, String taskId, int ver, String clientId, String script, String scriptHash,
                          String metaJson, String paramsJson, String opsJson, String createdBy, long createdAt) {

    public Set<String> ops() {
        Set<String> ops = new TreeSet<>();
        for (Object o : (List<?>) Json.readTree(opsJson)) ops.add(String.valueOf(o));
        return ops;
    }
}
