package io.prodaccess.runner;

import io.prodaccess.common.json.Json;
import io.prodaccess.common.protocol.RunEvent;
import io.prodaccess.common.protocol.RunSpec;
import io.prodaccess.common.protocol.ToolDef;
import io.prodaccess.common.tool.ToolRequest;
import io.prodaccess.common.tool.ToolResponse;
import io.prodaccess.common.tool.Tools;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Host side of {@code api.<role>.<tool>(args)}: enforces the run's tool set and guardrails (call budget, rps,
 * circuit breaker), performs the call and reports every call to the control process.
 * Returns a JSON envelope string so no host exception ever crosses into the guest.
 */
final class ToolInvoker {
    private final Map<String, ToolDef> tools = new HashMap<>();
    private final Tools clients;
    private final Emitter emitter;
    private final RunSpec.Limits limits;
    private int calls;
    private int consecutiveFailures;
    private long nextSlotNanos = System.nanoTime();

    ToolInvoker(List<ToolDef> allowed, Tools clients, Emitter emitter, RunSpec.Limits limits) {
        allowed.forEach(t -> tools.put(t.key(), t));
        this.clients = clients;
        this.emitter = emitter;
        this.limits = limits;
    }

    List<String> keys() {
        return tools.keySet().stream().sorted().toList();
    }

    String call(String key, String argsJson) {
        ToolDef tool = tools.get(key);
        if (tool == null) return fail("tool " + key + " is not permitted for this run", null, null);
        if (++calls > limits.maxApiCalls()) return fail("tool call budget exceeded (" + limits.maxApiCalls() + ")", null, null);
        if (consecutiveFailures >= limits.maxConsecutiveFailures()) {
            return fail("circuit open: " + consecutiveFailures + " consecutive failures", null, null);
        }
        pace();

        long t0 = System.nanoTime();
        Integer status = null;
        String err = null;
        try {
            ToolResponse r = clients.call(tool, ToolRequest.from(Json.read(argsJson, Map.class)));
            status = r.status();
            if (r.ok()) {
                consecutiveFailures = 0;
                Map<String, Object> ok = new LinkedHashMap<>();
                ok.put("ok", true);
                ok.put("value", r.value());
                return Json.write(ok);
            }
            err = "HTTP " + r.status();
            consecutiveFailures++;
            return fail(err, r.status(), Emitter.clip(r.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            err = "interrupted";
            return fail(err, null, null);
        } catch (Exception e) {
            err = e.getClass().getSimpleName() + ": " + e.getMessage();
            consecutiveFailures++;
            return fail(err, null, null);
        } finally {
            emitter.emit(RunEvent.tool(tool, status, (System.nanoTime() - t0) / 1_000_000, err));
        }
    }

    private static String fail(String message, Integer status, String body) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", message);
        m.put("status", status);
        m.put("body", body);
        return Json.write(m);
    }

    private void pace() {
        if (limits.rps() <= 0) return;
        long interval = (long) (1_000_000_000L / limits.rps());
        long now = System.nanoTime();
        long wait = nextSlotNanos - now;
        nextSlotNanos = Math.max(now, nextSlotNanos) + interval;
        if (wait > 0) {
            try {
                Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
