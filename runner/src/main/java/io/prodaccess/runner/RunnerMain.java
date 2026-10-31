package io.prodaccess.runner;

import io.prodaccess.common.json.Json;
import io.prodaccess.common.protocol.RunEvent;
import io.prodaccess.common.protocol.RunSpec;
import io.prodaccess.runner.gateway.SimpleGatewayClient;
import io.prodaccess.runner.sandbox.Sandbox;
import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

/**
 * Child process entry. Protocol: first stdin line = {@link RunSpec} JSON; later stdin lines are commands
 * ("cancel"); stdout = one {@link RunEvent} JSON per line. One process serves exactly one run and one
 * Client identity, then exits.
 *
 * <p>Exit codes: 0 success, 1 task failed, 2 bad spec, 124 timed out, 130 cancelled, 70 lost the parent.
 */
public final class RunnerMain {
    private static final int HEARTBEAT_MS = 2_000;
    private static final int MAX_SPEC_BYTES = 16 * 1024 * 1024;

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
        try (Sandbox sb = new Sandbox(spec.script(), Map.of("ops", __ -> "[]", "log", __ -> null, "step", __ -> null, "call", __ -> null),
                System.err)) {
            sandbox = sb;
            Value ns = sb.load();
            emitter.emit(RunEvent.withValue("inspect", Json.readTree(sb.inspect(ns))));
            return 0;
        } catch (PolyglotException e) {
            emitter.emit(RunEvent.error(Emitter.clip(describe(e))));
            return 1;
        }
    }

    private static int run(RunSpec spec, Emitter emitter) {
        ApiInvoker invoker = new ApiInvoker(spec.apis(), new SimpleGatewayClient(spec), emitter, spec.limits());
        Map<String, ProxyExecutable> host = Map.of(
                "ops", a -> Json.write(invoker.operations()),
                "call", a -> invoker.call(a[0].asString(), a[1].asString(), a[2].asString()),
                "log", a -> {
                    emitter.emit(RunEvent.log(a[0].asString(), Emitter.clip(a[1].asString())));
                    return null;
                },
                "step", a -> {
                    emitter.emit(RunEvent.step(a[0].asString(), a[1].asString()));
                    return null;
                });
        try (Sandbox sb = new Sandbox(spec.script(), host, System.err)) {
            sandbox = sb;
            Value ns = sb.load();
            Value out = sb.start(ns, spec.runId(), Json.write(spec.params()));
            String state = out.getMember("state").asString();
            if (state.equals("ok")) {
                String value = out.getMember("value").asString();
                if (value.length() > spec.limits().maxResultBytes()) {
                    emitter.emit(RunEvent.error("result larger than " + spec.limits().maxResultBytes() + " bytes"));
                    return 1;
                }
                emitter.emit(RunEvent.withValue("result", Json.readTree(value)));
                return 0;
            }
            emitter.emit(RunEvent.error(Emitter.clip(state.equals("error") ? out.getMember("message").asString() : "task did not settle")));
            return 1;
        } catch (PolyglotException e) {
            emitter.emit(RunEvent.error(Emitter.clip(describe(e))));
            return 1;
        }
    }

    private static String describe(PolyglotException e) {
        if (e.isCancelled()) return "cancelled";
        return e.getMessage();
    }

    /** Reads commands after the spec; EOF means the parent is gone, so exit instead of lingering. */
    private static void startControlThread(BufferedReader stdin) {
        Thread t = new Thread(() -> {
            try {
                String l;
                while ((l = stdin.readLine()) != null) {
                    if (l.equals("cancel")) cancel(130);
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
