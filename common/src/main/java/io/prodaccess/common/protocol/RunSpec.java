package io.prodaccess.common.protocol;

import java.util.List;
import java.util.Map;

/**
 * Versioned spec sent to a runner child as the first line on stdin.
 * Carries the API Forge credentials, so it must never go through env, args or files.
 */
public record RunSpec(
        int version,
        String runId,
        Mode mode,
        String actor,
        String script,
        Map<String, Object> params,
        /** Only the tools this run is allowed to call. */
        List<ToolDef> tools,
        Limits limits,
        ApiForge apiForge) {

    public static final int CURRENT_VERSION = 2;

    public enum Mode { RUN, INSPECT }

    public record Limits(long timeoutMs, int maxApiCalls, double rps, int maxConsecutiveFailures, int maxResultBytes) {}

    /** Where API Forge tools are sent and the prod-access identity they are sent as. */
    public record ApiForge(String baseUrl, String tokenUrl, String clientId, String clientSecret) {
        @Override
        public String toString() {
            return "ApiForge[baseUrl=" + baseUrl + ", clientId=" + clientId + ", clientSecret=***]";
        }
    }
}
