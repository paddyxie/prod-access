package io.prodaccess.common.tool;

import io.prodaccess.common.json.Json;
import io.prodaccess.common.protocol.RunSpec;
import io.prodaccess.common.protocol.ToolDef;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Performs tool calls, choosing the client by tool type. One instance serves one run (or one direct call):
 * every request carries that run's id and actor for attribution on the target's side.
 *
 * <p>API Forge calls use a plain OAuth2 client-credentials stand-in for the API Forge SDK: the bearer token is
 * cached and refreshed before expiry, and requests go to {@code baseUrl/<role><path>}. Replace
 * {@link #apiForge} with the SDK's generic client once its API is known.
 */
public final class Tools {
    private static final int MAX_BODY = 5 * 1024 * 1024;

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    private final RunSpec.ApiForge apiForge;
    private final String runId;
    private final String actor;
    private String token;
    private long tokenExpiresAtMs;

    public Tools(RunSpec.ApiForge apiForge, String runId, String actor) {
        this.apiForge = apiForge;
        this.runId = runId;
        this.actor = actor;
    }

    public ToolResponse call(ToolDef tool, ToolRequest req) throws IOException, InterruptedException {
        return switch (tool.type()) {
            case API_FORGE -> {
                ToolResponse r = send(tool, req, apiForge.baseUrl() + "/" + tool.role(), true);
                if (r.status() == 401) {
                    invalidateToken();
                    r = send(tool, req, apiForge.baseUrl() + "/" + tool.role(), true);
                }
                yield r;
            }
            case HTTP_CLIENT -> send(tool, req, tool.baseUrl(), false);
        };
    }

    private ToolResponse send(ToolDef tool, ToolRequest req, String base, boolean authenticate) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(req.url(base, tool.pathTemplate())))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                // attribution for the target's audit trail
                .header("X-Prod-Access-Run", runId)
                .header("X-Prod-Access-Actor", actor);
        if (authenticate) b.header("Authorization", "Bearer " + token());
        req.headers().forEach(b::header);
        String body = req.bodyJson();
        if (body != null) b.header("Content-Type", "application/json");
        b.method(tool.method().toUpperCase(), body != null ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody());
        HttpResponse<byte[]> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (resp.body().length > MAX_BODY) throw new IOException("response larger than " + MAX_BODY + " bytes");
        return new ToolResponse(resp.statusCode(), resp.headers().firstValue("Content-Type").orElse(""),
                new String(resp.body(), StandardCharsets.UTF_8));
    }

    private synchronized String token() throws IOException, InterruptedException {
        if (token != null && System.currentTimeMillis() < tokenExpiresAtMs - 30_000) return token;
        String form = "grant_type=client_credentials"
                + "&client_id=" + URLEncoder.encode(apiForge.clientId(), StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(apiForge.clientSecret(), StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(apiForge.tokenUrl()))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new IOException("API Forge token endpoint returned HTTP " + resp.statusCode());
        @SuppressWarnings("unchecked") Map<String, Object> j = Json.read(resp.body(), Map.class);
        token = (String) j.get("access_token");
        long ttl = j.get("expires_in") instanceof Number n ? n.longValue() : 300;
        tokenExpiresAtMs = System.currentTimeMillis() + ttl * 1000;
        return token;
    }

    private synchronized void invalidateToken() {
        token = null;
    }
}
