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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EndToEndTest {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static HttpServer gateway;
    /** "METHOD path | Authorization | X-Prod-Access-Actor" for every API call the mock gateway received. */
    static final List<String> received = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void startGateway() throws IOException {
        gateway = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        gateway.createContext("/oauth/token", ex -> {
            String form = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean ok = form.contains("client_id=cid") && form.contains("client_secret=sec");
            byte[] b = (ok ? "{\"access_token\":\"tok-1\",\"expires_in\":300}" : "{}").getBytes();
            ex.sendResponseHeaders(ok ? 200 : 401, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        gateway.createContext("/orders", ex -> {
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
        gateway.setExecutor(java.util.concurrent.Executors.newCachedThreadPool()); // the slow endpoint must not block others
        gateway.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        String base = "http://127.0.0.1:" + gateway.getAddress().getPort();
        r.add("spring.datasource.url", () -> "jdbc:h2:mem:e2e;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        r.add("prodaccess.gateway.base-url", () -> base);
        r.add("prodaccess.gateway.token-url", () -> base + "/oauth/token");
        r.add("prodaccess.runner.grace-ms", () -> "1500");
        r.add("prodaccess.runner.poll-ms", () -> "100");
        r.add("prodaccess.credentials.test.client-id", () -> "cid");
        r.add("prodaccess.credentials.test.client-secret", () -> "sec");
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

    String clientId;

    void seed() {
        if (clientId != null) return;
        JsonNode existing = call("alice", HttpMethod.GET, "/api/clients", null, 200);
        for (JsonNode c : existing) if (c.get("name").asString().equals("order-admin")) clientId = c.get("id").asString();
        if (clientId != null) return;
        {
            clientId = call("alice", HttpMethod.POST, "/api/clients",
                    Map.of("name", "order-admin", "environment", "PROD", "credentialRef", "test"), 200).get("id").asString();
            call("alice", HttpMethod.POST, "/api/apis", Map.of("service", "orders", "operation", "getOrder", "method", "GET", "pathTemplate", "/orders/{id}"), 200);
            call("alice", HttpMethod.POST, "/api/apis", Map.of("service", "orders", "operation", "replayOrder", "method", "POST", "pathTemplate", "/orders/{id}/replay"), 200);
            call("alice", HttpMethod.POST, "/api/apis", Map.of("service", "orders", "operation", "slow", "method", "GET", "pathTemplate", "/orders/{id}/slow"), 200);
        }
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

    String adhoc(String user, String script, Map<String, Object> params, String confirm, int expected) {
        JsonNode r = call(user, HttpMethod.POST, "/api/runs/adhoc",
                Map.of("clientId", clientId, "script", script, "params", params, "confirmClient", confirm == null ? "" : confirm), expected);
        return expected == 200 ? r.get("id").asString() : null;
    }

    static final String READ_TASK = """
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
    void readOnlyRunExecutesAsTheClientAndIsAudited() {
        seed();
        String id = adhoc("alice", READ_TASK, Map.of("ids", "o1,o2"), null, 200);
        JsonNode run = awaitRun(id, "SUCCEEDED", "FAILED");
        assertEquals("SUCCEEDED", run.get("status").asString(), run.toString());
        JsonNode result = parse(run.get("resultJson").asString());
        assertEquals(2, result.get("count").asInt());
        assertEquals("o1", result.get("first").get("id").asString());

        assertTrue(received.stream().anyMatch(s -> s.equals("GET /orders/orders/o1 | Bearer tok-1 | alice")), received.toString());
        JsonNode audit = call("alice", HttpMethod.GET, "/api/runs/" + id + "/api-calls", null, 200);
        assertEquals(2, audit.size());
        assertEquals("getOrder", audit.get(0).get("operation").asString());
        assertEquals(200, audit.get(0).get("status").asInt());
        JsonNode events = call("alice", HttpMethod.GET, "/api/runs/" + id + "/events", null, 200);
        assertTrue(events.toString().contains("lookup start") && events.toString().contains("found 2"));
        awaitNoChildren();
    }

    @Test
    void writeRunNeedsConfirmationAndSomeoneElsesApproval() {
        seed();
        String script = """
                export const params = { orderId: param.string() };
                export default async function main(ctx) { return await api.orders.replayOrder({ path: { id: ctx.params.orderId }, body: { force: true } }); }
                """;
        adhoc("alice", script, Map.of("orderId", "w1"), null, 400);          // client name not typed
        int before = received.size();
        String id = adhoc("alice", script, Map.of("orderId", "w1"), "order-admin", 200);
        assertEquals("PENDING_APPROVAL", call("alice", HttpMethod.GET, "/api/runs/" + id, null, 200).get("status").asString());
        sleep(800);
        assertEquals(before, received.size(), "nothing may reach the gateway before approval");

        call("alice", HttpMethod.POST, "/api/runs/" + id + "/approve", null, 403); // self-approval refused
        call("bob", HttpMethod.POST, "/api/runs/" + id + "/approve", null, 200);
        JsonNode run = awaitRun(id, "SUCCEEDED", "FAILED");
        assertEquals("SUCCEEDED", run.get("status").asString(), run.toString());
        assertEquals("bob", run.get("approvedBy").asString());
        assertTrue(received.stream().anyMatch(s -> s.startsWith("POST /orders/orders/w1/replay")));
        call("bob", HttpMethod.POST, "/api/runs/" + id + "/approve", null, 409);   // not approvable twice
        awaitNoChildren();
    }

    @Test
    void readWriteComesFromTheLiveApiListButASubmittedRunKeepsItsApproval() {
        seed();
        Map<String, Object> archive = new java.util.HashMap<>(Map.of("service", "orders", "operation", "archive", "method", "POST", "pathTemplate", "/orders/{id}/archive"));
        call("alice", HttpMethod.POST, "/api/apis", archive, 200);
        String script = "export default async function main() { return await api.orders.archive({ path: { id: 'a1' } }); }";
        String taskId = call("alice", HttpMethod.POST, "/api/tasks", Map.of("name", "archive", "clientId", clientId, "script", script), 200)
                .get("taskId").asString();
        assertTrue(call("alice", HttpMethod.GET, "/api/tasks/" + taskId, null, 200).get("hasWrite").asBoolean());
        String pending = call("alice", HttpMethod.POST, "/api/runs", Map.of("taskId", taskId, "params", Map.of(), "confirmClient", "order-admin"), 200)
                .get("id").asString();

        archive.put("access", "READ");
        call("alice", HttpMethod.POST, "/api/apis", archive, 200);

        // The task follows the list immediately, without saving a new version...
        assertFalse(call("alice", HttpMethod.GET, "/api/tasks/" + taskId, null, 200).get("hasWrite").asBoolean());
        JsonNode fresh = call("alice", HttpMethod.POST, "/api/runs", Map.of("taskId", taskId, "params", Map.of()), 200);
        assertEquals("QUEUED", fresh.get("status").asString());
        assertFalse(fresh.get("requiresApproval").asBoolean());
        // ...but the run submitted while it was WRITE still needs someone else's approval.
        JsonNode run = call("alice", HttpMethod.GET, "/api/runs/" + pending, null, 200);
        assertEquals("PENDING_APPROVAL", run.get("status").asString());
        assertTrue(run.get("requiresApproval").asBoolean());
        call("alice", HttpMethod.POST, "/api/runs/" + pending + "/approve", null, 403);
        call("alice", HttpMethod.POST, "/api/runs/" + pending + "/cancel", null, 200);
        awaitRun(fresh.get("id").asString(), "SUCCEEDED", "FAILED");
    }

    @Test
    void rejectedRunNeverExecutes() {
        seed();
        String script = "export default async function main() { return await api.orders.replayOrder({ path: { id: 'rej' } }); }";
        String id = adhoc("alice", script, Map.of(), "order-admin", 200);
        call("bob", HttpMethod.POST, "/api/runs/" + id + "/reject", Map.of("reason", "no"), 200);
        assertEquals("REJECTED", call("alice", HttpMethod.GET, "/api/runs/" + id, null, 200).get("status").asString());
        sleep(500);
        assertFalse(received.stream().anyMatch(s -> s.contains("/orders/rej/")));
    }

    @Test
    void scriptsCannotUseOperationsOutsideTheApiListOrHideApiAccess() {
        seed();
        adhoc("alice", "export default async function main() { return api.orders.nope({}); }", Map.of(), null, 400);
        adhoc("alice", "export default async function main() { const a = api; return a.orders.getOrder({}); }", Map.of(), null, 400);
        adhoc("alice", "export default async function main() { return api['orders']['replayOrder']({}); }", Map.of(), null, 400);
    }

    @Test
    void invalidParamsAreReportedPerField() {
        seed();
        JsonNode r = call("alice", HttpMethod.POST, "/api/runs/adhoc",
                Map.of("clientId", clientId, "script", READ_TASK, "params", Map.of("ids", "a,b,c,d,e,f"), "confirmClient", ""), 400);
        assertNotNull(r.get("fields").get("ids"));
    }

    @Test
    void runawayScriptIsTimedOutAndItsProcessIsGone() {
        seed();
        String script = """
                export const meta = { timeout: '2s' };
                export default async function main() { while (true) {} }
                """;
        String id = adhoc("alice", script, Map.of(), null, 200);
        JsonNode run = awaitRun(id, "TIMED_OUT", "FAILED", "SUCCEEDED");
        assertEquals("TIMED_OUT", run.get("status").asString(), run.toString());
        awaitNoChildren();
    }

    @Test
    void cancelKillsARunBlockedInsideAnApiCall() {
        seed();
        String script = "export default async function main() { return await api.orders.slow({ path: { id: 's1' } }); }";
        String id = adhoc("alice", script, Map.of(), null, 200);
        awaitRun(id, "RUNNING");
        sleep(1500);
        call("alice", HttpMethod.POST, "/api/runs/" + id + "/cancel", null, 200);
        JsonNode run = awaitRun(id, "CANCELLED", "FAILED", "SUCCEEDED");
        assertEquals("CANCELLED", run.get("status").asString(), run.toString());
        awaitNoChildren();
    }

    @Test
    void sandboxHasNoWayOut() {
        seed();
        String script = """
                export default async function main() {
                  const out = {};
                  for (const [k, f] of Object.entries({
                    hostClass: () => Java.type('java.lang.System').getenv('HOME'),
                    file: () => Java.type('java.io.File'),
                    importFs: () => import('/etc/passwd'),
                  })) { try { await f(); out[k] = 'REACHED'; } catch (e) { out[k] = 'blocked'; } }
                  out.globals = [typeof process, typeof require, typeof fetch, typeof XMLHttpRequest, typeof setTimeout].join(',');
                  return out;
                }
                """;
        JsonNode run = awaitRun(adhoc("alice", script, Map.of(), null, 200), "SUCCEEDED", "FAILED");
        JsonNode out = parse(run.get("resultJson").asString());
        assertEquals("blocked", out.get("hostClass").asString());
        assertEquals("blocked", out.get("file").asString());
        assertEquals("blocked", out.get("importFs").asString());
        assertEquals("undefined,undefined,undefined,undefined,undefined", out.get("globals").asString());
    }

    @Test
    void namedTasksAreVersionedAndPausingStopsDispatch() {
        seed();
        JsonNode v1 = call("alice", HttpMethod.POST, "/api/tasks", Map.of("name", "lookup", "clientId", clientId, "script", READ_TASK), 200);
        String taskId = v1.get("taskId").asString();
        JsonNode v2 = call("alice", HttpMethod.POST, "/api/tasks/" + taskId + "/versions",
                Map.of("clientId", clientId, "script", READ_TASK + "\n// v2"), 200);
        assertEquals(2, v2.get("ver").asInt());
        call("alice", HttpMethod.POST, "/api/tasks", Map.of("name", "lookup", "clientId", clientId, "script", READ_TASK), 409);

        call("alice", HttpMethod.POST, "/api/admin/pause", Map.of("paused", true), 200);
        String id = call("alice", HttpMethod.POST, "/api/runs", Map.of("taskId", taskId, "params", Map.of("ids", "p1")), 200).get("id").asString();
        sleep(1000);
        assertEquals("QUEUED", call("alice", HttpMethod.GET, "/api/runs/" + id, null, 200).get("status").asString());
        call("alice", HttpMethod.POST, "/api/admin/pause", Map.of("paused", false), 200);
        assertEquals("SUCCEEDED", awaitRun(id, "SUCCEEDED", "FAILED").get("status").asString());
    }

    @Test
    void disabledClientBlocksSubmission() {
        seed();
        String other = call("alice", HttpMethod.POST, "/api/clients",
                Map.of("name", "disabled-one", "credentialRef", "test", "enabled", false), 200).get("id").asString();
        call("alice", HttpMethod.POST, "/api/runs/adhoc",
                Map.of("clientId", other, "script", READ_TASK, "params", Map.of("ids", "a")), 409);
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

    static JsonNode parse(String s) {
        return JSON.readTree(s);
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { throw new IllegalStateException(e); }
    }
}
