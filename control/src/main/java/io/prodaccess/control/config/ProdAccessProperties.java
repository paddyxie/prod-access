package io.prodaccess.control.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("prodaccess")
public record ProdAccessProperties(
        @DefaultValue("") String devUser,
        @DefaultValue Gateway gateway,
        @DefaultValue Runner runner,
        @DefaultValue Limits limits,
        @DefaultValue Map<String, Credential> credentials) {

    public record Gateway(@DefaultValue("http://localhost:9000") String baseUrl,
                          @DefaultValue("http://localhost:9000/oauth/token") String tokenUrl) {}

    public record Runner(@DefaultValue("runner/build/libs/runner.jar") String jar,
                         @DefaultValue("") String java,
                         @DefaultValue("256") int heapMb,
                         @DefaultValue("4") int maxConcurrent,
                         @DefaultValue("5000") long graceMs,
                         @DefaultValue("20000") long heartbeatTimeoutMs,
                         @DefaultValue("30000") long lostAfterMs,
                         @DefaultValue("true") boolean reconcile) {}

    /** Ceilings; a task may lower them via meta but never raise them. */
    public record Limits(@DefaultValue("600000") long timeoutMs,
                         @DefaultValue("5000") int maxApiCalls,
                         @DefaultValue("10") double rps,
                         @DefaultValue("10") int maxConsecutiveFailures,
                         @DefaultValue("1048576") int maxResultBytes,
                         @DefaultValue("2000000") int maxLogBytes) {}

    public record Credential(String clientId, String clientSecret, String scope) {}
}
