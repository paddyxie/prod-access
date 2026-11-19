package io.prodaccess.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.prodaccess.control.process.ChildProcessRunner;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs the real app against a mock target: {@code /oauth/token} and {@code /orders/...} stand in for API Forge,
 * {@code /direct/...} for an endpoint reached by an HTTP Client tool. Team "orders-team" (alice, bob) owns role
 * {@code orders}; carol is only in "billing-team".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EndToEndTest {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static HttpServer target;
    /** "METHOD path | Authorization | X-Prod-Access-Actor" for every tool call the mock target received. */
    static final List<String> received = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void startTarget() throws IOException {
        target = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        target.createContext("/oauth/token", ex -> {
            String form = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean ok = form.contains("client_id=prod-access") && form.contains("client_secret=sec");
            byte[] b = (ok ? "{\"access_token\":\"tok-1\",\"expires_in\":300}" : "{}").getBytes();
            ex.sendResponseHeaders(ok ? 200 : 401, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        for (String prefix : List.of("/orders", "/direct")) {
            target.createContext(prefix, ex -> {
                ex.getRequestBody().readAllBytes();
                received.add(ex.getRequestMethod() + " " + ex.getRequestURI().getPath() + " | "
                        + ex.getRequestHeaders().getFirst("Authorization") + " | " + ex.getRequestHeaders().getFirst("X-Prod-Access-Actor"));
                if (ex.getRequestURI().getPath().endsWith("/slow")) {
                    try { Thread.sleep(30_000); } catch (InterruptedException ignored) { }
                }
                byte[] b = ("{\"id\":\"" + ex.getRequestURI().getPath().replaceAll(".*/orders/", "").split("/")[0] + "\",\"state\":\"FAILED\"}").getBytes();
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, b.length);
                ex.getResponseBody().write(b);
                ex.close();
            });
        }
        target.setExecutor(java.util.concurrent.Executors.newCachedThreadPool()); // the slow endpoint must not block others
        target.start();
    }

    static String base() {
        return "http://127.0.0.1:" + target.getAddress().getPort();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> "jdbc:h2:mem:e2e;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        r.add("prodaccess.api-forge.base-url", EndToEndTest::base);
        r.add("prodaccess.api-forge.token-url", () -> base() + "/oauth/token");
        r.add("prodaccess.api-forge.client-id", () -> "prod-access");
        r.add("prodaccess.api-forge.client-secret", () -> "sec");
        r.add("prodaccess.cmdb.role-owners.orders[0]", () -> "orders-team");
        r.add("prodaccess.cmdb.role-owners.billing[0]", () -> "billing-team");
        r.add("prodaccess.cmdb.teams.orders-team[0]", () -> "alice");
        r.add("prodaccess.cmdb.teams.orders-team[1]", () -> "bob");
        r.add("prodaccess.cmdb.teams.billing-team[0]", () -> "carol");
        r.add("prodaccess.runner.grace-ms", () -> "1500");
        r.add("prodaccess.runner.poll-ms", () -> "100");
    }

    @LocalServerPort int port;
    @Autowired ChildProcessRunner runner;

    // ---- helpers

    JsonNode call(String user, HttpMethod m, String path, Object body, int expectedStatus) {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("X-User", user)
                .header("Content-Type", "application/json")
                .method(m.name(), body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        HttpResponse<String> resp;
        try {
            resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
        assertEquals(expectedStatus, resp.statusCode(), () -> path + " -> " + resp.body());
        return resp.body().isBlank() ? null : JSON.readTree(resp.body());
    }

    /** Registers a tool once (as alice, an owner of the role) and returns its id. */
    String tool(String role, String name, String type, String method, String path, String baseUrl) {
        for (JsonNode t : call("alice", HttpMethod.GET, "/api/tools?role=" + role, null, 200)) {
            if (t.get("name").asString().equals(name)) return t.get("id").asString();
        }
        Map<String, Object> in = new HashMap<>(Map.of("role", role, "name", name, "type", type, "method", method, "pathTemplate", path));
        if (baseUrl != null) in.put("baseUrl", baseUrl);
        return call("alice", HttpMethod.POST, "/api/tools", in, 200).get("id").asString();
    }

    void seed() {
        tool("orders", "getOrder", "API_FORGE", "GET", "/orders/{id}", null);
        tool("orders", "slow", "API_FORGE", "GET", "/orders/{id}/slow", null);
        tool("orders", "lookup", "HTTP_CLIENT", "GET", "/orders/{id}", base() + "/direct");
    }

    JsonNode awaitRun(String runId, String... terminal) {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode run = call("alice", HttpMethod.GET, "/api/runs/" + runId, null, 200);
            if (List.of(terminal).contains(run.get("status").asString())) return run;
            try { Thread.sleep(100); } catch (InterruptedException e) { throw new IllegalStateException(e); }
        }
        throw new AssertionError("run " + runId + " did not reach " + List.of(terminal));
    }

    void awaitNoChildren() {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (ProcessHandle.current().children().findAny().isEmpty() && runner.liveRegistered() == 0) return;
            try { Thread.sleep(100); } catch (InterruptedException e) { throw new IllegalStateException(e); }
        }
        throw new AssertionError("child processes still alive: " + ProcessHandle.current().children().toList());
    }

    String adhoc(String user, String script, Map<String, Object> params, int expected) {
        JsonNode r = call(user, HttpMethod.POST, "/api/runs/adhoc", Map.of("script", script, "params", params), expected);
        return expected == 200 ? r.get("id").asString() : null;
    }

    static final String LOOKUP = """
            export const meta = { title: 'look up orders' };
            export const params = { ids: param.list(param.string(), { max: 5 }), note: param.string({ default: 'x' }) };
            export default async function main(ctx) {
              const found = await ctx.step('lookup', async () => {
                const out = [];
                for (const id of ctx.params.ids) out.push(await api.orders.getOrder({ path: { id } }));
                return out;
              });
              log.info('found', found.length);
              return { count: found.length, first: found[0] };
            }
            """;

    // ---- tests

    @Test
    void scriptCallsApiForgeToolsAsProdAccessAndIsAudited() {
        seed();
        String id = adhoc("alice", LOOKUP, Map.of("ids", "o1,o2"), 200);
        JsonNode run = awaitRun(id, "SUCCEEDED", "FAILED");
        assertEquals("SUCCEEDED", run.get("status").asString(), run.toString());
        JsonNode result = JSON.readTree(run.get("resultJson").asString());
        assertEquals(2, result.get("count").asInt());
        assertEquals("o1", result.get("first").get("id").asString());

        assertTrue(received.contains("GET /orders/orders/o1 | Bearer tok-1 | alice"), received.toString());
        JsonNode calls = call("alice", HttpMethod.GET, "/api/runs/" + id + "/tool-calls", null, 200);
        assertEquals(2, calls.size());
        assertEquals("getOrder", calls.get(0).get("tool").asString());
        assertEquals("API_FORGE", calls.get(0).get("type").asString());
        assertEquals(200, calls.get(0).get("status").asInt());
        JsonNode events = call("alice", HttpMethod.GET, "/api/runs/" + id + "/events", null, 200);
        assertTrue(events.toString().contains("lookup start") && events.toString().contains("found 2"));
        awaitNoChildren();
    }

    @Test
    void httpClientToolsGoStraightToTheirEndpointWithoutTheApiForgeToken() {
        seed();
        String id = adhoc("bob", "export default async function main() { return await api.orders.lookup({ path: { id: 'h1' } }); }", Map.of(), 200);
        JsonNode run = awaitRun(id, "SUCCEEDED", "FAILED");
        assertEquals("SUCCEEDED", run.get("status").asString(), run.toString());
        assertTrue(received.contains("GET /direct/orders/h1 | null | bob"), received.toString());
        assertEquals("HTTP_CLIENT", call("bob", HttpMethod.GET, "/api/runs/" + id + "/tool-calls", null, 200).get(0).get("type").asString());
    }

    @Test
    void onlyTeamsThatOwnTheRoleMayRunOrEditItsTools() {
        seed();
        int before = received.size();
        adhoc("carol", LOOKUP, Map.of("ids", "c1"), 403);
        assertEquals(before, received.size(), "a refused run never reaches the target");
        call("carol", HttpMethod.POST, "/api/tools",
                Map.of("role", "orders", "name", "sneaky", "type", "API_FORGE", "method", "POST", "pathTemplate", "/x"), 403);
        JsonNode me = call("carol", HttpMethod.GET, "/api/me", null, 200);
        assertEquals("billing-team", me.get("teams").get(0).asString());
    }

    @Test
    void aSingleToolCallRunsDirectlyAndIsRecordedAsARun() {
        seed();
        String toolId = tool("orders", "getOrder", "API_FORGE", "GET", "/orders/{id}", null);
        JsonNode run = call("bob", HttpMethod.POST, "/api/runs/call", Map.of("toolId", toolId, "request", Map.of("path", Map.of("id", "d1"))), 200);
        assertEquals("CALL", run.get("kind").asString());
        assertEquals("SUCCEEDED", run.get("status").asString(), run.toString());
        assertEquals("orders.getOrder", run.get("toolKey").asString());
        JsonNode result = JSON.readTree(run.get("resultJson").asString());
        assertEquals(200, result.get("status").asInt());
        assertEquals("d1", result.get("body").get("id").asString());
        assertTrue(received.contains("GET /orders/orders/d1 | Bearer tok-1 | bob"), received.toString());
        assertEquals(1, call("bob", HttpMethod.GET, "/api/runs/" + run.get("id").asString() + "/tool-calls", null, 200).size());

        call("carol", HttpMethod.POST, "/api/runs/call", Map.of("toolId", toolId, "request", Map.of("path", Map.of("id", "d2"))), 403);
    }

    @Test
    void scriptsCannotUseToolsOutsideTheRegistryOrHideApiAccess() {
        seed();
        adhoc("alice", "export default async function main() { return api.orders.nope({}); }", Map.of(), 400);
        adhoc("alice", "export default async function main() { const a = api; return a.orders.getOrder({}); }", Map.of(), 400);
        adhoc("alice", "export default async function main() { return api['orders']['getOrder']({}); }", Map.of(), 400);
    }

    @Test
    void invalidParamsAreReportedPerField() {
        seed();
        JsonNode r = call("alice", HttpMethod.POST, "/api/runs/adhoc", Map.of("script", LOOKUP, "params", Map.of("ids", "a,b,c,d,e,f")), 400);
        assertNotNull(r.get("fields").get("ids"));
    }

    @Test
    void runawayScriptIsTimedOutAndItsProcessIsGone() {
        String script = """
                export const meta = { timeout: '2s' };
                export default async function main() { while (true) {} }
                """;
        String id = adhoc("alice", script, Map.of(), 200);
        JsonNode run = awaitRun(id, "TIMED_OUT", "FAILED", "SUCCEEDED");
        assertEquals("TIMED_OUT", run.get("status").asString(), run.toString());
        awaitNoChildren();
    }

    @Test
    void cancelKillsARunBlockedInsideAToolCall() {
        seed();
        String id = adhoc("alice", "export default async function main() { return await api.orders.slow({ path: { id: 's1' } }); }", Map.of(), 200);
        awaitRun(id, "RUNNING");
        sleep(1500);
        call("alice", HttpMethod.POST, "/api/runs/" + id + "/cancel", null, 200);
        JsonNode run = awaitRun(id, "CANCELLED", "FAILED", "SUCCEEDED");
        assertEquals("CANCELLED", run.get("status").asString(), run.toString());
        awaitNoChildren();
    }

    @Test
    void sandboxHasNoWayOut() {
        String script = """
                export default async function main() {
                  return [typeof Javy, typeof eval, typeof __host, typeof fetch, typeof require, typeof process,
                          typeof setTimeout, typeof std, typeof os, typeof Java].join(',');
                }
                """;
        JsonNode run = awaitRun(adhoc("alice", script, Map.of(), 200), "SUCCEEDED", "FAILED");
        assertEquals("\"undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined\"",
                run.get("resultJson").asString(), run.toString());
    }

    @Test
    void namedTasksAreVersionedAndPausingStopsDispatch() {
        seed();
        JsonNode v1 = call("alice", HttpMethod.POST, "/api/tasks", Map.of("name", "lookup", "script", LOOKUP), 200);
        String taskId = v1.get("taskId").asString();
        JsonNode v2 = call("alice", HttpMethod.POST, "/api/tasks/" + taskId + "/versions", Map.of("script", LOOKUP + "\n// v2"), 200);
        assertEquals(2, v2.get("ver").asInt());
        call("alice", HttpMethod.POST, "/api/tasks", Map.of("name", "lookup", "script", LOOKUP), 409);
        assertEquals("orders", call("alice", HttpMethod.GET, "/api/tasks/" + taskId, null, 200).get("roles").get(0).asString());

        call("alice", HttpMethod.POST, "/api/admin/pause", Map.of("paused", true), 200);
        String id = call("alice", HttpMethod.POST, "/api/runs", Map.of("taskId", taskId, "params", Map.of("ids", "p1")), 200).get("id").asString();
        sleep(1000);
        assertEquals("QUEUED", call("alice", HttpMethod.GET, "/api/runs/" + id, null, 200).get("status").asString());
        call("alice", HttpMethod.POST, "/api/admin/pause", Map.of("paused", false), 200);
        assertEquals("SUCCEEDED", awaitRun(id, "SUCCEEDED", "FAILED").get("status").asString());
    }

    @Test
    void aToolRemovedWhileQueuedFailsTheRunBeforeItStarts() {
        String toolId = tool("orders", "temp", "API_FORGE", "GET", "/orders/{id}", null);
        call("alice", HttpMethod.POST, "/api/admin/pause", Map.of("paused", true), 200);
        String id;
        try {
            id = adhoc("alice", "export default async function main() { return api.orders.temp({ path: { id: 't1' } }); }", Map.of(), 200);
            call("alice", HttpMethod.DELETE, "/api/tools/" + toolId, null, 200);
        } finally {
            call("alice", HttpMethod.POST, "/api/admin/pause", Map.of("paused", false), 200);
        }
        JsonNode run = awaitRun(id, "FAILED", "SUCCEEDED");
        assertEquals("FAILED", run.get("status").asString());
        assertTrue(run.get("error").asString().contains("removed from the registry"), run.toString());
        assertFalse(received.stream().anyMatch(s -> s.contains("/t1")));
    }

    @Test
    void reconcilerKillsStrayChildProcesses() throws Exception {
        Process stray = new ProcessBuilder("sleep", "300").start();
        try {
            runner.reconcile();
            assertTrue(stray.waitFor(5, java.util.concurrent.TimeUnit.SECONDS), "stray child must be killed");
        } finally {
            stray.destroyForcibly();
        }
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
