package io.prodaccess.common.protocol;

import java.util.List;
import java.util.Map;

/**
 * Versioned spec sent to a runner child as the first line on stdin.
 * Carries the Client credentials, so it must never go through env, args or files.
 */
public record RunSpec(
        int version,
        String runId,
        Mode mode,
        String actor,
        String script,
        Map<String, Object> params,
        /** Only the operations this run is allowed to call. */
        List<ApiDef> apis,
        Limits limits,
        ClientAuth client,
        Gateway gateway) {

    public static final int CURRENT_VERSION = 1;

    public enum Mode { RUN, INSPECT }

    public record Limits(long timeoutMs, int maxApiCalls, double rps, int maxConsecutiveFailures, int maxResultBytes) {}

    public record ClientAuth(String clientId, String clientSecret, String scope) {
        @Override
        public String toString() {
            return "ClientAuth[clientId=" + clientId + ", clientSecret=***]";
        }
    }

    public record Gateway(String baseUrl, String tokenUrl) {}
}
