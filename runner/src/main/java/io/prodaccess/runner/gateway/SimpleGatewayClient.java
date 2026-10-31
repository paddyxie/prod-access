package io.prodaccess.runner.gateway;

import io.prodaccess.common.json.Json;
import io.prodaccess.common.protocol.ApiDef;
import io.prodaccess.common.protocol.RunSpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Placeholder gateway client: OAuth2 client_credentials against {@code tokenUrl}, bearer token cached and
 * refreshed before expiry, requests sent to {@code baseUrl/<service><path>}. Swap for the real SDK.
 */
public final class SimpleGatewayClient implements GatewayClient {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]+)}");
    private static final int MAX_BODY = 5 * 1024 * 1024;

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    private final RunSpec.ClientAuth auth;
    private final RunSpec.Gateway gateway;
    private final String runId;
    private final String actor;
    private String token;
    private long tokenExpiresAtMs;

    public SimpleGatewayClient(RunSpec spec) {
        this.auth = spec.client();
        this.gateway = spec.gateway();
        this.runId = spec.runId();
        this.actor = spec.actor();
    }

    @Override
    public Response call(ApiDef api, Map<String, String> path, Map<String, String> query, Map<String, String> headers, String bodyJson)
            throws IOException, InterruptedException {
        Response r = send(api, path, query, headers, bodyJson);
        if (r.status() == 401) {
            invalidateToken();
            r = send(api, path, query, headers, bodyJson);
        }
        return r;
    }

    private Response send(ApiDef api, Map<String, String> path, Map<String, String> query, Map<String, String> headers, String bodyJson)
            throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url(api, path, query)))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token())
                .header("Accept", "application/json")
                // attribution for the gateway-side audit trail
                .header("X-Prod-Access-Run", runId)
                .header("X-Prod-Access-Actor", actor);
        headers.forEach(b::header);
        boolean hasBody = bodyJson != null && !bodyJson.equals("null");
        if (hasBody) b.header("Content-Type", "application/json");
        b.method(api.method().toUpperCase(),
                hasBody ? HttpRequest.BodyPublishers.ofString(bodyJson) : HttpRequest.BodyPublishers.noBody());
        HttpResponse<byte[]> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (resp.body().length > MAX_BODY) throw new IOException("response larger than " + MAX_BODY + " bytes");
        return new Response(resp.statusCode(), resp.headers().firstValue("Content-Type").orElse(""),
                new String(resp.body(), StandardCharsets.UTF_8));
    }

    private String url(ApiDef api, Map<String, String> path, Map<String, String> query) {
        Matcher m = PLACEHOLDER.matcher(api.pathTemplate());
        StringBuilder p = new StringBuilder();
        while (m.find()) {
            String v = path.get(m.group(1));
            if (v == null) throw new IllegalArgumentException("missing path parameter '" + m.group(1) + "'");
            m.appendReplacement(p, Matcher.quoteReplacement(URLEncoder.encode(v, StandardCharsets.UTF_8).replace("+", "%20")));
        }
        m.appendTail(p);
        StringBuilder u = new StringBuilder(gateway.baseUrl().replaceAll("/+$", ""))
                .append('/').append(api.service()).append(p);
        char sep = '?';
        for (var e : query.entrySet()) {
            u.append(sep).append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=').append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            sep = '&';
        }
        return u.toString();
    }

    private synchronized String token() throws IOException, InterruptedException {
        if (token != null && System.currentTimeMillis() < tokenExpiresAtMs - 30_000) return token;
        String form = "grant_type=client_credentials"
                + "&client_id=" + URLEncoder.encode(auth.clientId(), StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(auth.clientSecret(), StandardCharsets.UTF_8)
                + (auth.scope() == null ? "" : "&scope=" + URLEncoder.encode(auth.scope(), StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(URI.create(gateway.tokenUrl()))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new IOException("token endpoint returned HTTP " + resp.statusCode());
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
