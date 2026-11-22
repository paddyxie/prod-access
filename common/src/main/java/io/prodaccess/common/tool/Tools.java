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

    private final java.util.function.Consumer<io.prodaccess.common.protocol.RunEvent> evidence;
    private final java.util.function.Supplier<String> action;

    public Tools(RunSpec.ApiForge apiForge, String runId, String actor,
                 java.util.function.Consumer<io.prodaccess.common.protocol.RunEvent> evidence,
                 java.util.function.Supplier<String> action) {
        this.apiForge = apiForge; this.runId = runId; this.actor = actor; this.evidence = evidence; this.action = action;
    }

    public ToolResponse call(ToolDef tool, Map<String, Object> args) throws IOException, InterruptedException {
        return call(tool, args, null);
    }

    public ToolResponse call(ToolDef tool, Map<String,Object> args, String rejection) throws IOException, InterruptedException {
        String invocation = java.util.UUID.randomUUID().toString();
        AuditCapture.Copy input = AuditCapture.json(args);
        Map<String,Object> start = new java.util.LinkedHashMap<>();
        start.put("id", invocation); start.put("toolVersionId", tool.versionId()); start.put("actor", actor);
        start.put("actionId", action.get()); start.put("inputJson", input.text()); start.put("inputTruncated", input.truncated());
        emit("invocation_start", start);
        Map<String,Object> end = new java.util.LinkedHashMap<>(); end.put("id", invocation);
        try {
            if (rejection != null) throw new IllegalStateException(rejection);
            io.prodaccess.common.schema.Schema.validate(tool.inputSchemaJson(), args, "request");
            ToolRequest request = ToolRequest.from(args);
            ToolResponse result;
            if (tool.type() == io.prodaccess.common.protocol.ToolType.API_FORGE) {
                result = send(tool, request, apiForge.baseUrl() + "/" + tool.role(), true, invocation, 1);
                if (result.status() == 401) {
                    invalidateToken();
                    result = send(tool, request, apiForge.baseUrl() + "/" + tool.role(), true, invocation, 2);
                }
            } else result = send(tool, request, tool.baseUrl(), false, invocation, 1);
            AuditCapture.Copy output = AuditCapture.json(result.value());
            end.put("outputJson", output.text()); end.put("outputTruncated", output.truncated());
            if (result.ok()) io.prodaccess.common.schema.Schema.validate(tool.outputSchemaJson(), result.value(), "response");
            end.put("status", result.ok() ? "SUCCEEDED" : "FAILED");
            if (!result.ok()) end.put("error", "HTTP " + result.status());
            return result;
        } catch (IOException | InterruptedException | RuntimeException e) {
            end.put("status", "FAILED"); end.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        } finally { emit("invocation_end", end); }
    }

    private ToolResponse send(ToolDef tool, ToolRequest req, String base, boolean authenticate, String invocation, int attempt)
            throws IOException, InterruptedException {
        String url = req.url(base, tool.pathTemplate());
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json").header("X-Prod-Access-Run", runId).header("X-Prod-Access-Actor", actor);
        if (authenticate) b.header("Authorization", "Bearer " + token());
        req.headers().forEach((name, value) -> {
            if (name.equalsIgnoreCase("X-Prod-Access-Run") || name.equalsIgnoreCase("X-Prod-Access-Actor")
                    || (authenticate && name.equalsIgnoreCase("Authorization"))) {
                throw new IllegalArgumentException("reserved attribution or gateway credential header: " + name);
            }
            b.header(name, value);
        });
        String body = req.bodyJson();
        if (body != null) b.header("Content-Type", "application/json");
        b.method(tool.method().toUpperCase(), body != null ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody());
        HttpRequest request = b.build();
        String id = java.util.UUID.randomUUID().toString();
        AuditCapture.Copy captured = AuditCapture.body(body);
        Map<String,Object> start = new java.util.LinkedHashMap<>();
        start.put("id", id); start.put("invocationId", invocation); start.put("attempt", attempt);
        start.put("method", tool.method()); start.put("url", AuditCapture.url(url));
        start.put("requestHeadersJson", AuditCapture.json(request.headers().map()).text());
        start.put("requestBody", captured.text()); start.put("requestTruncated", captured.truncated());
        emit("http_start", start);
        Map<String,Object> end = new java.util.LinkedHashMap<>(); end.put("id", id);
        try {
            HttpResponse<byte[]> resp = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String text = new String(resp.body(), StandardCharsets.UTF_8);
            AuditCapture.Copy copy = AuditCapture.body(text);
            end.put("responseStatus", resp.statusCode()); end.put("responseHeadersJson", AuditCapture.json(resp.headers().map()).text());
            end.put("responseBody", copy.text()); end.put("responseTruncated", copy.truncated());
            end.put("status", resp.statusCode() >= 200 && resp.statusCode() < 300 ? "SUCCEEDED" : "HTTP_ERROR");
            if (resp.body().length > MAX_BODY) throw new IOException("response larger than " + MAX_BODY + " bytes");
            return new ToolResponse(resp.statusCode(), resp.headers().firstValue("Content-Type").orElse(""), text);
        } catch (IOException | InterruptedException | RuntimeException e) {
            end.put("status", "TRANSPORT_ERROR"); end.put("error", e.getClass().getSimpleName() + ": " + e.getMessage()); throw e;
        } finally { emit("http_end", end); }
    }

    private void emit(String type, Map<String,Object> data) {
        evidence.accept(io.prodaccess.common.protocol.RunEvent.withValue(type, data));
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
