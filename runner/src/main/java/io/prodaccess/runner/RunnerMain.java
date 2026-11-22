package io.prodaccess.runner;

import io.prodaccess.common.json.Json;
import io.prodaccess.common.protocol.RunEvent;
import io.prodaccess.common.protocol.RunSpec;
import io.prodaccess.common.tool.AuditCapture;
import io.prodaccess.common.tool.Tools;
import io.prodaccess.runner.sandbox.Sandbox;
import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Child process entry. Protocol: first stdin line = {@link RunSpec} JSON; later stdin lines are commands
 * ("cancel"); stdout = one {@link RunEvent} JSON per line. One process serves exactly one run, then exits.
 *
 * <p>Exit codes: 0 success, 1 workflow failed, 2 bad spec, 124 timed out, 130 cancelled, 70 lost the parent.
 */
public final class RunnerMain {
    private static final int HEARTBEAT_MS = 2_000;
    private static final int MAX_SPEC_BYTES = 16 * 1024 * 1024;

    private static final java.util.concurrent.BlockingQueue<String> acknowledgements = new java.util.concurrent.LinkedBlockingQueue<>();
    private static volatile Sandbox sandbox;
    private static volatile int cancelExit = -1;

    private RunnerMain() {}

    public static void main(String[] args) throws Exception {
        PrintStream protocol = new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
        System.setOut(System.err); // nothing but the protocol may reach the real stdout
        Emitter emitter = new Emitter(protocol);

        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line = stdin.readLine();
        if (line == null || line.length() > MAX_SPEC_BYTES) die(emitter, 2, "missing or oversized spec");
        RunSpec spec;
        try {
            spec = Json.read(line, RunSpec.class);
        } catch (RuntimeException e) {
            die(emitter, 2, "unreadable spec: " + e.getMessage());
            return;
        }
        if (spec.version() != RunSpec.CURRENT_VERSION) die(emitter, 2, "unsupported spec version " + spec.version());

        long deadline = System.currentTimeMillis() + spec.limits().timeoutMs();
        startControlThread(stdin);
        startHeartbeat(emitter);
        startDeadline(emitter, deadline);

        int code;
        try {
            code = spec.mode() == RunSpec.Mode.INSPECT ? inspect(spec, emitter) : run(spec, emitter);
        } catch (Throwable t) {
            code = cancelExit >= 0 ? cancelExit : 1;
            emitter.emit(RunEvent.error(Emitter.clip(t.toString())));
        }
        if (cancelExit >= 0) code = cancelExit;
        emitter.emit(RunEvent.of("exit"));
        Runtime.getRuntime().halt(code);
    }

    private static int inspect(RunSpec spec, Emitter emitter) {
        Sandbox.Host host = new Sandbox.Host() {
            public String tools() { return "[]"; }
            public String call(String tool, String argsJson, String actionId) { return "{\"ok\":false,\"error\":\"tools cannot be called while inspecting\"}"; }
            public void log(String level, String message) {}
            public String beginStep(String name, String parentId, String inputJson) { return UUID.randomUUID().toString(); }
            public void endStep(String id, String name, String state, String payloadJson) {}
        };
        try (Sandbox sb = new Sandbox(host)) {
            sandbox = sb;
            Sandbox.Outcome out = sb.inspect(spec.script());
            if (!out.ok()) {
                emitter.emit(RunEvent.error(Emitter.clip(out.error())));
                return 1;
            }
            emitter.emit(RunEvent.withValue("inspect", Json.readTree(out.value())));
            return 0;
        }
    }

    private static int run(RunSpec spec, Emitter emitter) {
        // A tool host call is synchronous. Supply its explicitly resolved action ID to the client for its duration.
        var callAction = new AtomicReference<String>();
        Tools clients = new Tools(spec.apiForge(), spec.runId(), spec.actor(), ev -> emitEvidence(emitter, ev), callAction::get);
        ToolInvoker invoker = new ToolInvoker(spec.tools(), clients, spec.limits());
        Sandbox.Host host = new Sandbox.Host() {
            public String tools() { return Json.write(invoker.keys()); }
            public String call(String tool, String argsJson, String actionId) {
                callAction.set(actionId.isEmpty() ? null : actionId);
                try {
                    return invoker.call(tool, argsJson);
                } finally {
                    callAction.set(null);
                }
            }
            public void log(String level, String message) { emitter.emit(RunEvent.log(level, Emitter.clip(message))); }
            public String beginStep(String name, String parentId, String inputJson) {
                String id = UUID.randomUUID().toString();
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("id", id);
                data.put("parentId", parentId.isEmpty() ? null : parentId);
                data.put("name", name);
                data.put("inputJson", AuditCapture.json(Json.readTree(inputJson)).text());
                emitEvidence(emitter, RunEvent.withValue("action_start", data));
                emitter.emit(RunEvent.step(name, "start"));
                return id;
            }
            public void endStep(String id, String name, String state, String payloadJson) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("id", id);
                data.put("status", state.equals("ok") ? "SUCCEEDED" : "FAILED");
                Object payload = Json.readTree(payloadJson);
                if (state.equals("ok")) {
                    data.put("resultJson", AuditCapture.json(payload).text());
                } else {
                    data.put("error", Emitter.clip(String.valueOf(payload)));
                }
                emitEvidence(emitter, RunEvent.withValue("action_end", data));
                emitter.emit(RunEvent.step(name, state));
            }
        };
        try (Sandbox sb = new Sandbox(host)) {
            sandbox = sb;
            Sandbox.Outcome out = sb.run(spec.script(), Json.write(spec.runId()), Json.write(spec.params()));
            if (!out.ok()) {
                emitter.emit(RunEvent.error(Emitter.clip(out.error())));
                return 1;
            }
            if (out.value().length() > spec.limits().maxResultBytes()) {
                emitter.emit(RunEvent.error("result larger than " + spec.limits().maxResultBytes() + " bytes"));
                return 1;
            }
            emitter.emit(RunEvent.withValue("result", Json.readTree(out.value())));
            return 0;
        }
    }

    private static void emitEvidence(Emitter emitter, RunEvent event) {
        emitter.emit(event);
        if (event.t().endsWith("_start")) {
            String id = String.valueOf(((java.util.Map<?,?>) event.value()).get("id"));
            try {
                String acknowledged = acknowledgements.poll(10, java.util.concurrent.TimeUnit.SECONDS);
                if (!id.equals(acknowledged)) throw new IllegalStateException("audit persistence was not acknowledged");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); throw new IllegalStateException("interrupted while recording audit", e);
            }
        }
    }

    /** Reads commands after the spec; EOF means the parent is gone, so exit instead of lingering. */
    private static void startControlThread(BufferedReader stdin) {
        Thread t = new Thread(() -> {
            try {
                String l;
                while ((l = stdin.readLine()) != null) {
                    if (l.equals("cancel")) cancel(130);
                    else if (l.startsWith("audit-ack:")) acknowledgements.offer(l.substring(10));
                }
            } catch (IOException ignored) {
                // treated like EOF
            }
            Runtime.getRuntime().halt(70);
        }, "control");
        t.setDaemon(true);
        t.start();
    }

    private static void startHeartbeat(Emitter emitter) {
        Thread t = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(HEARTBEAT_MS);
                    emitter.emit(RunEvent.of("hb"));
                }
            } catch (InterruptedException ignored) {
                // exiting
            }
        }, "heartbeat");
        t.setDaemon(true);
        t.start();
    }

    /** Self-deadline: cancel the sandbox at the limit; if that does not end the process, halt shortly after. */
    private static void startDeadline(Emitter emitter, long deadline) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(Math.max(0, deadline - System.currentTimeMillis()));
                emitter.emit(RunEvent.error("timed out"));
                cancel(124);
                Thread.sleep(3_000);
            } catch (InterruptedException ignored) {
                return;
            }
            Runtime.getRuntime().halt(124);
        }, "deadline");
        t.setDaemon(true);
        t.start();
    }

    private static void cancel(int exitCode) {
        cancelExit = exitCode;
        Sandbox sb = sandbox;
        if (sb != null) sb.cancel();
        else Runtime.getRuntime().halt(exitCode);
    }

    private static void die(Emitter emitter, int code, String msg) {
        emitter.emit(RunEvent.error(msg));
        Runtime.getRuntime().halt(code);
    }
}
