package io.prodaccess.control.config;

import io.prodaccess.common.protocol.RunSpec;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("prodaccess")
public record ProdAccessProperties(
        @DefaultValue("") String devUser,
        @DefaultValue ApiForge apiForge,
        @DefaultValue Cmdb cmdb,
        @DefaultValue Runner runner,
        @DefaultValue Limits limits) {

    /** Where API Forge tools are sent, and the prod-access identity. Move the secret to Vault. */
    public record ApiForge(@DefaultValue("http://localhost:9000") String baseUrl,
                           @DefaultValue("http://localhost:9000/oauth/token") String tokenUrl,
                           @DefaultValue("") String clientId,
                           @DefaultValue("") String clientSecret) {
        public RunSpec.ApiForge toSpec() {
            return new RunSpec.ApiForge(baseUrl, tokenUrl, clientId, clientSecret);
        }
    }

    /** Stand-in for CMDB until its API is known: which teams own each role, and who is in each team. */
    public record Cmdb(@DefaultValue Map<String, List<String>> roleOwners,
                       @DefaultValue Map<String, List<String>> teams) {}

    public record Runner(@DefaultValue("runner/build/libs/runner.jar") String jar,
                         @DefaultValue("") String java,
                         @DefaultValue("256") int heapMb,
                         @DefaultValue("4") int maxConcurrent,
                         @DefaultValue("5000") long graceMs,
                         @DefaultValue("20000") long heartbeatTimeoutMs,
                         @DefaultValue("30000") long lostAfterMs,
                         @DefaultValue("true") boolean reconcile) {}

    /** Ceilings; a workflow may lower them via meta but never raise them. */
    public record Limits(@DefaultValue("600000") long timeoutMs,
                         @DefaultValue("5000") int maxApiCalls,
                         @DefaultValue("10") double rps,
                         @DefaultValue("10") int maxConsecutiveFailures,
                         @DefaultValue("1048576") int maxResultBytes,
                         @DefaultValue("2000000") int maxLogBytes) {}
}
