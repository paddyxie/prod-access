package io.prodaccess.runner;

import io.prodaccess.common.json.Json;
import io.prodaccess.common.protocol.ApiDef;
import io.prodaccess.common.protocol.RunEvent;
import io.prodaccess.common.protocol.RunSpec;
import io.prodaccess.runner.gateway.GatewayClient;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Host side of {@code api.<service>.<operation>(args)}: enforces the operation set and the run's guardrails
 * (call budget, rps, circuit breaker), performs the call and reports every call to the control process.
 * Returns a JSON envelope string so no host exception ever crosses into the guest.
 */
final class ApiInvoker {
    private final Map<String, ApiDef> apis = new HashMap<>();
    private final GatewayClient gateway;
    private final Emitter emitter;
    private final RunSpec.Limits limits;
    private int calls;
    private int consecutiveFailures;
    private long nextSlotNanos = System.nanoTime();

    ApiInvoker(List<ApiDef> allowed, GatewayClient gateway, Emitter emitter, RunSpec.Limits limits) {
        allowed.forEach(a -> apis.put(a.key(), a));
        this.gateway = gateway;
        this.emitter = emitter;
        this.limits = limits;
    }

    List<String> operations() {
        return apis.keySet().stream().sorted().toList();
    }

    String call(String service, String operation, String argsJson) {
        ApiDef api = apis.get(service + "." + operation);
        if (api == null) return fail("operation " + service + "." + operation + " is not permitted for this run", null, null);
        if (++calls > limits.maxApiCalls()) return fail("API call budget exceeded (" + limits.maxApiCalls() + ")", null, null);
        if (consecutiveFailures >= limits.maxConsecutiveFailures()) {
            return fail("circuit open: " + consecutiveFailures + " consecutive failures", null, null);
        }
        pace();

        long t0 = System.nanoTime();
        Integer status = null;
        String err = null;
        try {
            @SuppressWarnings("unchecked") Map<String, Object> args = Json.read(argsJson, Map.class);
            GatewayClient.Response r = gateway.call(api,
                    strings(args.get("path")), strings(args.get("query")), strings(args.get("headers")),
                    args.get("body") == null ? null : Json.write(args.get("body")));
            status = r.status();
            if (r.status() >= 200 && r.status() < 300) {
                consecutiveFailures = 0;
                return Json.write(Map.of("ok", true, "value", parse(r)));
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
            long ms = (System.nanoTime() - t0) / 1_000_000;
            emitter.emit(RunEvent.api(service, operation, api.method(), api.pathTemplate(), status, ms, err));
        }
    }

    private Object parse(GatewayClient.Response r) {
        if (r.body() == null || r.body().isBlank()) return null;
        if (r.contentType().toLowerCase().contains("json")) {
            try {
                return Json.readTree(r.body());
            } catch (IllegalArgumentException ignored) {
                // fall through to text
            }
        }
        return r.body();
    }

    private static String fail(String message, Integer status, String body) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", message);
        m.put("status", status);
        m.put("body", body);
        return Json.write(m);
    }

    private static Map<String, String> strings(Object o) {
        Map<String, String> out = new LinkedHashMap<>();
        if (o instanceof Map<?, ?> m) m.forEach((k, v) -> {
            if (v != null) out.put(String.valueOf(k), String.valueOf(v));
        });
        return out;
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
