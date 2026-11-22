package io.prodaccess.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    static final java.util.concurrent.ConcurrentHashMap<String,java.util.concurrent.atomic.AtomicInteger> retryCounts = new java.util.concurrent.ConcurrentHashMap<>();
    static volatile org.springframework.jdbc.core.simple.JdbcClient auditDb;
    static volatile boolean sawPersistedStart;
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
                if (ex.getRequestURI().getPath().contains("/audit-start/")) {
                    String executionId = ex.getRequestHeaders().getFirst("X-Prod-Access-Run");
                    sawPersistedStart = auditDb.sql("SELECT COUNT(*) FROM api_calls a JOIN tool_invocations i ON i.id=a.invocation_id WHERE i.execution_id=? AND a.status='RUNNING'")
                            .param(executionId).query(Integer.class).single() > 0;
                }
                received.add(ex.getRequestMethod() + " " + ex.getRequestURI().getPath() + " | "
                        + ex.getRequestHeaders().getFirst("Authorization") + " | " + ex.getRequestHeaders().getFirst("X-Prod-Access-Actor"));
                if (ex.getRequestURI().getPath().endsWith("/slow")) {
                    try { Thread.sleep(30_000); } catch (InterruptedException ignored) { }
                }
                byte[] b = ("{\"id\":\"" + ex.getRequestURI().getPath().replaceAll(".*/orders/", "").split("/")[0] + "\",\"state\":\"FAILED\"}").getBytes();
                ex.getResponseHeaders().add("Content-Type", "application/json");
                boolean retry = ex.getRequestURI().getPath().contains("/retry/")
                        && retryCounts.computeIfAbsent(ex.getRequestURI().getPath(), key -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet() == 1;
                ex.sendResponseHeaders(retry ? 401 : 200, b.length);
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
    @Autowired org.springframework.jdbc.core.simple.JdbcClient db;
    @Autowired io.prodaccess.control.tool.ToolService toolService;

    // ---- helpers

    JsonNode call(String user, HttpMethod m, String path, Object body, int expectedStatus) {
        auditDb = db;
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
        ensureCatalog(role);
        for (JsonNode t : call("alice", HttpMethod.GET, "/api/tools?role=" + role, null, 200)) {
            if (t.get("name").asString().equals(name)) return t.get("id").asString();
        }
        Map<String, Object> in = new HashMap<>(Map.of("role", role, "name", name, "type", type, "method", method, "pathTemplate", path));
        in.put("inputSchemaJson", "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}},\"query\":{\"type\":\"object\"},\"headers\":{\"type\":\"object\"},\"body\":{}},\"additionalProperties\":false}");
        in.put("outputSchemaJson", "{\"type\":\"object\",\"required\":[\"id\",\"state\"],\"properties\":{\"id\":{\"type\":\"string\"},\"state\":{\"type\":\"string\"}}}");
        if (baseUrl != null) in.put("baseUrl", baseUrl);
        return call("alice", HttpMethod.POST, "/api/tools", in, 200).get("id").asString();
    }

    void ensureCatalog(String role) {
        for (JsonNode r : call("alice", HttpMethod.GET, "/api/catalog/roles", null, 200)) {
            if (r.get("code").asString().equals(role)) return;
        }
        String serviceId = null;
        for (JsonNode service : call("alice", HttpMethod.GET, "/api/catalog/services", null, 200)) {
            if (service.get("code").asString().equals("commerce")) serviceId = service.get("id").asString();
        }
        if (serviceId == null) serviceId = call("alice", HttpMethod.POST, "/api/catalog/services", Map.of("code", "commerce", "name", "Commerce"), 200).get("id").asString();
        call("alice", HttpMethod.POST, "/api/catalog/roles", Map.of("serviceId", serviceId, "code", role, "name", role, "systemType", "WEB"), 200);
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
        JsonNode input = call("alice", HttpMethod.GET, "/api/runs/"+id+"/inputs", null, 200);
        assertEquals("o1,o2", JSON.readTree(input.get("submittedJson").asString()).get("ids").asString());
        assertEquals(2, JSON.readTree(input.get("resolvedJson").asString()).get("ids").size());
        assertEquals("x", JSON.readTree(input.get("resolvedJson").asString()).get("note").asString());
        assertEquals(2, result.get("count").asInt());
        assertEquals("o1", result.get("first").get("id").asString());

        assertTrue(received.contains("GET /orders/orders/o1 | Bearer tok-1 | alice"), received.toString());
        JsonNode calls = call("alice", HttpMethod.GET, "/api/runs/" + id + "/tool-calls", null, 200);
        assertEquals(2, calls.size());
        assertEquals("getOrder", calls.get(0).get("tool").asString());
        assertEquals("API_FORGE", calls.get(0).get("type").asString());
        assertEquals("SUCCEEDED", calls.get(0).get("status").asString());
        assertEquals("alice", calls.get(0).get("actor").asString());
        assertEquals("commerce", calls.get(0).get("service").asString());
        assertNotNull(calls.get(0).get("actionId"));
        JsonNode httpCalls = call("alice", HttpMethod.GET, "/api/runs/" + id + "/api-calls", null, 200);
        assertEquals(2, httpCalls.size());
        assertEquals(200, httpCalls.get(0).get("responseStatus").asInt());
        assertTrue(httpCalls.get(0).get("url").asString().contains("/orders/orders/o1"));
        assertTrue(httpCalls.get(0).get("responseBody").asString().contains("o1"));
        assertFalse(httpCalls.toString().contains("tok-1"));
        JsonNode actions = call("alice", HttpMethod.GET, "/api/runs/" + id + "/actions", null, 200);
        assertEquals(1, actions.size());
        assertEquals("SUCCEEDED", actions.get(0).get("status").asString());
        assertTrue(actions.get(0).get("resultJson").asString().contains("o1"));
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
                Map.of("role", "orders", "name", "sneaky", "type", "API_FORGE", "method", "POST", "pathTemplate", "/x", "inputSchemaJson", "{}", "outputSchemaJson", "{}"), 403);
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
        JsonNode requests = call("alice", HttpMethod.GET, "/api/runs/" + id + "/api-calls", null, 200);
        assertEquals(1, requests.size());
        assertEquals("UNKNOWN", requests.get(0).get("status").asString());
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
    void namedWorkflowsAreVersionedAndPausingStopsDispatch() {
        seed();
        JsonNode v1 = call("alice", HttpMethod.POST, "/api/workflows", Map.of("name", "lookup", "script", LOOKUP), 200);
        String workflowId = v1.get("workflowId").asString();
        JsonNode v2 = call("alice", HttpMethod.POST, "/api/workflows/" + workflowId + "/versions", Map.of("script", LOOKUP + "\n// v2"), 200);
        assertEquals(2, v2.get("ver").asInt());
        call("alice", HttpMethod.POST, "/api/workflows", Map.of("name", "lookup", "script", LOOKUP), 409);
        assertEquals("orders", call("alice", HttpMethod.GET, "/api/workflows/" + workflowId, null, 200).get("roles").get(0).asString());

        call("alice", HttpMethod.POST, "/api/admin/pause", Map.of("paused", true), 200);
        String id = call("alice", HttpMethod.POST, "/api/runs", Map.of("workflowId", workflowId, "params", Map.of("ids", "p1")), 200).get("id").asString();
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
    void executionUsesTheWorkflowVersionDisplayedByTheForm() {
        JsonNode first = call("alice", HttpMethod.POST, "/api/workflows", Map.of("name", "publish-race", "script",
                "export default function main() { return 'first'; }"), 200);
        String workflowId = first.get("workflowId").asString();
        call("alice", HttpMethod.POST, "/api/workflows/" + workflowId + "/versions", Map.of("script",
                "export default function main() { return 'second'; }"), 200);
        JsonNode execution = call("alice", HttpMethod.POST, "/api/runs", Map.of("workflowId", workflowId, "version", 1), 200);
        JsonNode finished = awaitRun(execution.get("id").asString(), "SUCCEEDED", "FAILED");
        assertEquals(1, finished.get("ver").asInt());
        assertEquals("first", JSON.readTree(finished.get("resultJson").asString()).asString());
    }

    @Test
    void workflowPinsToolContractsAndHistoricalEvidenceSurvivesRetirement() {
        String toolId = tool("orders", "pinned", "HTTP_CLIENT", "GET", "/orders/{id}", base() + "/direct");
        JsonNode original = call("alice", HttpMethod.GET, "/api/tools/" + toolId, null, 200);
        JsonNode version = call("alice", HttpMethod.POST, "/api/workflows", Map.of("name", "pinned-contract", "script",
                "export default async function main() { return api.orders.pinned({path:{id:'pin1'}}); }"), 200);
        Map<String,Object> update = new HashMap<>(Map.of("role", "orders", "name", "pinned", "type", "HTTP_CLIENT", "method", "GET", "pathTemplate", "/changed/{id}", "baseUrl", base() + "/direct",
                "inputSchemaJson", original.get("inputSchemaJson").asString(), "outputSchemaJson", "{\"type\":\"string\"}"));
        JsonNode changed = call("alice", HttpMethod.PUT, "/api/tools/" + toolId, update, 200);
        assertEquals(2, changed.get("ver").asInt());
        String originalVersion = original.get("versionId").asString();
        assertEquals(originalVersion, version.get("toolBindings").get(0).get("versionId").asString());
        String anotherTool = tool("orders", "otherPinned", "HTTP_CLIENT", "GET", "/orders/{id}", base() + "/direct");
        var request = Map.of("path", Map.of("id", "pinDirect"));
        call("alice", HttpMethod.POST, "/api/runs/call", Map.of("toolId", anotherTool, "toolVersionId", originalVersion, "request", request), 400);
        JsonNode pinnedDirect = call("alice", HttpMethod.POST, "/api/runs/call", Map.of("toolId", toolId, "toolVersionId", originalVersion, "request", request), 200);
        assertEquals("SUCCEEDED", pinnedDirect.get("status").asString());
        assertEquals(originalVersion, pinnedDirect.get("toolVersionId").asString());
        assertTrue(received.stream().anyMatch(entry -> entry.startsWith("GET /direct/orders/pinDirect |")));
        JsonNode invalidOutput = call("alice", HttpMethod.POST, "/api/runs/call", Map.of("toolId", toolId, "request", Map.of("path", Map.of("id", "pin2"))), 200);
        assertEquals("FAILED", invalidOutput.get("status").asString());
        JsonNode outputEvidence = call("alice", HttpMethod.GET, "/api/runs/" + invalidOutput.get("id").asString() + "/tool-calls", null, 200).get(0);
        assertFalse(outputEvidence.get("outputJson").isNull());
        assertEquals(200, call("alice", HttpMethod.GET, "/api/runs/" + invalidOutput.get("id").asString() + "/api-calls", null, 200).get(0).get("responseStatus").asInt());
        String id = call("bob", HttpMethod.POST, "/api/runs", Map.of("workflowId", version.get("workflowId").asString(), "params", Map.of()), 200).get("id").asString();
        assertEquals("SUCCEEDED", awaitRun(id, "SUCCEEDED", "FAILED").get("status").asString());
        JsonNode evidence = call("alice", HttpMethod.GET, "/api/runs/" + id + "/tool-calls", null, 200).get(0);
        assertEquals(original.get("versionId").asString(), evidence.get("toolVersionId").asString());
        call("alice", HttpMethod.DELETE, "/api/tools/" + toolId, null, 200);
        assertEquals(1, call("alice", HttpMethod.GET, "/api/runs/" + id + "/tool-calls", null, 200).size());
        assertEquals(2, call("alice", HttpMethod.GET, "/api/tools/" + toolId + "/versions", null, 200).size());
    }

    @Test
    void invalidToolInputIsAuditedWithoutSendingHttp() {
        String toolId = tool("orders", "validated", "HTTP_CLIENT", "GET", "/orders/{id}", base() + "/direct");
        int before = received.size();
        JsonNode run = call("alice", HttpMethod.POST, "/api/runs/call", Map.of("toolId", toolId, "request", Map.of("path", Map.of("id", 123))), 200);
        assertEquals("FAILED", run.get("status").asString());
        assertEquals(before, received.size());
        String id = run.get("id").asString();
        assertEquals(1, call("alice", HttpMethod.GET, "/api/runs/" + id + "/tool-calls", null, 200).size());
        assertEquals(0, call("alice", HttpMethod.GET, "/api/runs/" + id + "/api-calls", null, 200).size());
    }

    @Test
    void workflowOutputContractFailureKeepsTheActualOutput() {
        String id = adhoc("alice", "export const output = {type:'string'};\nexport default function main() { return {count:1}; }", Map.of(), 200);
        JsonNode run = awaitRun(id, "SUCCEEDED", "FAILED");
        assertEquals("FAILED", run.get("status").asString());
        assertTrue(run.get("error").asString().contains("workflow output"));
        assertEquals(1, JSON.readTree(run.get("resultJson").asString()).get("count").asInt());
    }

    @Test
    void everyGatewayHttpAttemptIsRecordedAndStartPrecedesTheRequest() {
        String toolId = tool("orders", "retry", "API_FORGE", "GET", "/retry/{id}", null);
        JsonNode execution = call("alice", HttpMethod.POST, "/api/runs/call", Map.of("toolId", toolId, "request", Map.of("path", Map.of("id", "retry1"))), 200);
        assertEquals("SUCCEEDED", execution.get("status").asString());
        JsonNode calls = call("alice", HttpMethod.GET, "/api/runs/"+execution.get("id").asString()+"/api-calls", null, 200);
        assertEquals(2, calls.size()); assertEquals(401, calls.get(0).get("responseStatus").asInt()); assertEquals(200, calls.get(1).get("responseStatus").asInt());
        assertEquals(calls.get(0).get("invocationId").asString(), calls.get(1).get("invocationId").asString());
        tool("orders", "auditStart", "HTTP_CLIENT", "GET", "/audit-start/{id}", base()+"/direct");
        sawPersistedStart = false;
        String id = adhoc("alice", "export default function main() { return api.orders.auditStart({path:{id:'start1'}}); }", Map.of(), 200);
        assertEquals("SUCCEEDED", awaitRun(id,"SUCCEEDED","FAILED").get("status").asString());
        assertTrue(sawPersistedStart, "the target must only be called after the parent persists the API start record");
    }

    @Test
    void databasePreventsActionsFromBeingAttachedToAnotherExecution() {
        JsonNode a = awaitRun(adhoc("alice", "export default function main(){ return 1; }", Map.of(), 200), "SUCCEEDED", "FAILED");
        JsonNode b = awaitRun(adhoc("alice", "export default function main(){ return 2; }", Map.of(), 200), "SUCCEEDED", "FAILED");
        String actionId = java.util.UUID.randomUUID().toString();
        db.sql("INSERT INTO execution_actions (id,execution_id,name,status,started_at) VALUES (?,?,?,'RUNNING',?)")
                .params(actionId, a.get("id").asString(), "parent", System.currentTimeMillis()).update();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> db.sql("INSERT INTO execution_actions (id,execution_id,parent_action_id,name,status,started_at) VALUES (?,?,?,?,'RUNNING',?)")
                .params(java.util.UUID.randomUUID().toString(), b.get("id").asString(), actionId, "cross-execution", System.currentTimeMillis()).update());
    }

    @Test
    void requestResponseEvidenceIsSearchableAndCredentialsAreRedacted() {
        String toolId = tool("orders", "evidenceBody", "HTTP_CLIENT", "POST", "/orders/{id}", base()+"/direct");
        JsonNode run = call("bob", HttpMethod.POST, "/api/runs/call", Map.of("toolId", toolId, "request", Map.of(
                "path", Map.of("id", "body1"), "headers", Map.of("Authorization", "Bearer private-credential"),
                "body", Map.of("operation", "repair", "password", "private-password"))), 200);
        assertEquals("SUCCEEDED", run.get("status").asString());
        String id=run.get("id").asString();
        JsonNode executionInput = call("alice", HttpMethod.GET, "/api/runs/"+id+"/inputs", null, 200);
        assertFalse(executionInput.toString().contains("private-credential"));
        assertFalse(executionInput.toString().contains("private-password"));
        assertFalse(run.get("paramsJson").asString().contains("private-credential"));
        assertTrue(received.contains("POST /direct/orders/body1 | Bearer private-credential | bob"));
        JsonNode request=call("alice", HttpMethod.GET, "/api/runs/"+id+"/api-calls", null, 200).get(0);
        assertEquals("POST", request.get("method").asString());
        assertTrue(request.get("requestBody").asString().contains("repair"));
        assertFalse(request.toString().contains("private-credential"));
        assertFalse(request.toString().contains("private-password"));
        assertEquals("body1",JSON.readTree(request.get("responseBody").asString()).get("id").asString());
        JsonNode matches=call("alice",HttpMethod.GET,"/api/audit/tool-invocations?actor=bob&service=commerce&role=orders&tool=evidenceBody",null,200);
        assertEquals(1,matches.size()); assertEquals(id,matches.get(0).get("executionId").asString());
        assertEquals(0,call("alice",HttpMethod.GET,"/api/audit/tool-invocations?actor=alice&tool=evidenceBody",null,200).size());
    }

    @Test
    void runnerStopsBeforeHttpWhenAuditPersistenceFails() {
        String toolId=tool("orders","auditFailure","HTTP_CLIENT","GET","/orders/{id}",base()+"/direct");
        int before=received.size();
        var spec=new io.prodaccess.common.protocol.RunSpec(io.prodaccess.common.protocol.RunSpec.CURRENT_VERSION,
                java.util.UUID.randomUUID().toString(),io.prodaccess.common.protocol.RunSpec.Mode.RUN,"alice",
                "export default function main(){return api.orders.auditFailure({path:{id:'never-sent'}});}",Map.of(),List.of(toolService.get(toolId).def()),
                new io.prodaccess.common.protocol.RunSpec.Limits(10_000,10,100,10,1_000_000),null);
        var outcome=runner.execute(spec,event->{ if(event.t().equals("http_start")) throw new IllegalStateException("database unavailable"); },()->false,()->{});
        assertEquals(ChildProcessRunner.Status.FAILED,outcome.status());
        assertEquals(before,received.size());
        awaitNoChildren();
    }

    @Test
    void overlappingWorkflowActionsKeepTheirOwnCallsAndResults() {
        tool("orders", "overlapLookup", "HTTP_CLIENT", "GET", "/orders/{id}", base() + "/direct");
        String id = adhoc("alice", """
                export default async function main(ctx) {
                  let release;
                  const waiting = new Promise(resolve => { release = resolve; });
                  const first = ctx.step('first', async action => {
                    await Promise.resolve();
                    return action.api.orders.overlapLookup({path: {id: 'first-order'}});
                  });
                  const second = ctx.step('second', async action => {
                    await waiting;
                    return action.step('inner', inner => inner.api.orders.overlapLookup({path: {id: 'second-order'}}));
                  });
                  await first;
                  release();
                  return second;
                }
                """, Map.of(), 200);
        JsonNode run = awaitRun(id, "SUCCEEDED", "FAILED");
        assertEquals("SUCCEEDED", run.get("status").asString(), run.toString());
        Map<String, JsonNode> actions = new HashMap<>();
        call("alice", HttpMethod.GET, "/api/runs/" + id + "/actions", null, 200)
                .forEach(action -> actions.put(action.get("name").asString(), action));
        assertEquals(3, actions.size());
        assertTrue(actions.get("first").get("parentActionId").isNull());
        assertTrue(actions.get("second").get("parentActionId").isNull());
        assertEquals(actions.get("second").get("id").asString(), actions.get("inner").get("parentActionId").asString());
        assertEquals("first-order", JSON.readTree(actions.get("first").get("resultJson").asString()).get("id").asString());
        assertEquals("second-order", JSON.readTree(actions.get("second").get("resultJson").asString()).get("id").asString());

        Map<String, String> actionByOrder = new HashMap<>();
        call("alice", HttpMethod.GET, "/api/runs/" + id + "/tool-calls", null, 200).forEach(invocation -> {
            String order = JSON.readTree(invocation.get("inputJson").asString()).get("path").get("id").asString();
            actionByOrder.put(order, invocation.get("actionId").asString());
        });
        assertEquals(Map.of("first-order", actions.get("first").get("id").asString(),
                "second-order", actions.get("inner").get("id").asString()), actionByOrder);
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
