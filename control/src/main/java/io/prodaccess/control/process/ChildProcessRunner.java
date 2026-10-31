package io.prodaccess.control.process;

import io.prodaccess.common.json.Json;
import io.prodaccess.common.protocol.RunEvent;
import io.prodaccess.common.protocol.RunSpec;
import io.prodaccess.control.config.ProdAccessProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Starts, feeds, watches and kills runner child processes. Every child is registered the moment it starts and
 * is watched by the calling thread until it is confirmed dead. The supervision layers:
 * <ol>
 *   <li>child: self-deadline halt, exit on stdin EOF (see RunnerMain)</li>
 *   <li>this class: deadline + grace, heartbeat timeout, cancel, bounded draining of stdout/stderr</li>
 *   <li>{@link #reconcile()}: any descendant process that is not in the registry is killed</li>
 *   <li>{@link #failStop}: if a kill cannot be confirmed, the whole control process exits (the container restarts)</li>
 * </ol>
 */
@Component
public class ChildProcessRunner {
    private static final Logger log = LoggerFactory.getLogger(ChildProcessRunner.class);
    private static final int MAX_LINE_BYTES = 4 * 1024 * 1024;
    private static final int STDERR_TAIL = 4096;

    public enum Status { SUCCEEDED, FAILED, CANCELLED, TIMED_OUT }

    public record Outcome(Status status, String resultJson, String error) {}

    private final ProdAccessProperties props;
    private final Object lock = new Object();
    private final Map<Long, String> registry = new HashMap<>(); // pid -> runId

    ChildProcessRunner(ProdAccessProperties props) {
        this.props = props;
    }

    /**
     * Runs one spec to completion. {@code sink} receives every event; {@code cancelRequested} is polled;
     * {@code heartbeat} is called periodically while the child is alive.
     */
    public Outcome execute(RunSpec spec, Consumer<RunEvent> sink, BooleanSupplier cancelRequested, Runnable heartbeat) {
        Process process;
        synchronized (lock) {
            try {
                process = new ProcessBuilder(command()).start();
            } catch (IOException e) {
                return new Outcome(Status.FAILED, null, "cannot start runner: " + e.getMessage());
            }
            registry.put(process.pid(), spec.runId());
        }
        try {
            return supervise(spec, process, sink, cancelRequested, heartbeat);
        } finally {
            killAndConfirm(process);
            synchronized (lock) {
                registry.remove(process.pid());
            }
        }
    }

    private Outcome supervise(RunSpec spec, Process process, Consumer<RunEvent> sink, BooleanSupplier cancelRequested,
                              Runnable heartbeat) {
        ProdAccessProperties.Runner cfg = props.runner();
        long start = System.currentTimeMillis();
        long deadline = start + spec.limits().timeoutMs() + cfg.graceMs();

        var state = new Object() {
            volatile long lastActivity = System.currentTimeMillis();
            volatile String result;
            volatile String lastError;
            volatile boolean oversized;
        };
        var stderrTail = new ByteArrayOutputStream();

        Thread out = Thread.ofVirtual().name("run-" + spec.runId() + "-out").start(() -> {
            try (InputStream in = process.getInputStream()) {
                byte[] line;
                while ((line = readLine(in)) != null) {
                    state.lastActivity = System.currentTimeMillis();
                    RunEvent ev;
                    try {
                        ev = Json.read(new String(line, StandardCharsets.UTF_8), RunEvent.class);
                    } catch (RuntimeException e) {
                        continue; // not protocol; ignore rather than trust it
                    }
                    switch (ev.t()) {
                        case "result" -> state.result = Json.write(ev.value());
                        case "error" -> state.lastError = ev.msg();
                        default -> { }
                    }
                    sink.accept(ev);
                }
            } catch (OversizedLine e) {
                state.oversized = true;
            } catch (IOException ignored) {
                // process gone
            }
        });
        Thread err = Thread.ofVirtual().name("run-" + spec.runId() + "-err").start(() -> {
            try (InputStream in = process.getErrorStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) { // always drain, keep only a bounded tail
                    synchronized (stderrTail) {
                        stderrTail.write(buf, 0, n);
                        if (stderrTail.size() > STDERR_TAIL * 2) {
                            byte[] all = stderrTail.toByteArray();
                            stderrTail.reset();
                            stderrTail.write(all, all.length - STDERR_TAIL, STDERR_TAIL);
                        }
                    }
                }
            } catch (IOException ignored) {
                // process gone
            }
        });

        OutputStream stdin = process.getOutputStream(); // kept open: EOF tells the child its parent is gone
        try {
            stdin.write((Json.write(spec) + "\n").getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        } catch (IOException e) {
            return new Outcome(Status.FAILED, null, "cannot send spec to runner: " + e.getMessage());
        }

        boolean cancelSent = false;
        long cancelAt = 0;
        Status forced = null;
        String forcedReason = null;
        long lastBeat = 0;
        try {
            while (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                long now = System.currentTimeMillis();
                if (now - lastBeat > 3000) {
                    heartbeat.run();
                    lastBeat = now;
                }
                if (state.oversized) {
                    forced = Status.FAILED;
                    forcedReason = "runner produced an oversized output line";
                } else if (now > deadline) {
                    forced = Status.TIMED_OUT;
                    forcedReason = "timed out";
                } else if (now - state.lastActivity > cfg.heartbeatTimeoutMs()) {
                    forced = Status.FAILED;
                    forcedReason = "runner unresponsive (no heartbeat)";
                } else if (cancelSent && now - cancelAt > cfg.graceMs()) {
                    forced = Status.CANCELLED;
                    forcedReason = "cancelled";
                } else if (!cancelSent && cancelRequested.getAsBoolean()) {
                    cancelSent = true;
                    cancelAt = now;
                    try {
                        stdin.write("cancel\n".getBytes(StandardCharsets.UTF_8));
                        stdin.flush();
                    } catch (IOException ignored) {
                        forced = Status.CANCELLED;
                        forcedReason = "cancelled";
                    }
                }
                if (forced != null) {
                    killAndConfirm(process);
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            killAndConfirm(process);
            forced = Status.FAILED;
            forcedReason = "supervisor interrupted";
        }

        join(out);
        join(err);
        int exit = process.isAlive() ? -1 : process.exitValue();

        if (forced != null) return new Outcome(forced, null, forcedReason);
        if (exit == 0 && state.result != null) return new Outcome(Status.SUCCEEDED, state.result, null);
        if (exit == 124) return new Outcome(Status.TIMED_OUT, null, "timed out");
        if (exit == 130 || (cancelSent && exit != 0)) return new Outcome(Status.CANCELLED, null, "cancelled");
        String detail = state.lastError != null ? state.lastError : "runner exited with code " + exit;
        synchronized (stderrTail) {
            if (state.lastError == null && stderrTail.size() > 0) {
                detail += ": " + stderrTail.toString(StandardCharsets.UTF_8).strip();
            }
        }
        return new Outcome(Status.FAILED, null, detail);
    }

    private List<String> command() {
        ProdAccessProperties.Runner cfg = props.runner();
        String java = cfg.java().isBlank() ? System.getProperty("java.home") + "/bin/java" : cfg.java();
        return new ArrayList<>(List.of(java, "-Xmx" + cfg.heapMb() + "m", "-XX:+ExitOnOutOfMemoryError",
                "-XX:TieredStopAtLevel=1", "-Dpolyglot.engine.WarnInterpreterOnly=false", "-jar", cfg.jar()));
        // No environment is inherited beyond what the JVM needs: credentials only travel over stdin.
    }

    // ---- kill & invariants

    private void killAndConfirm(Process p) {
        if (!p.isAlive()) return;
        p.destroy();
        try {
            if (p.waitFor(2, TimeUnit.SECONDS)) return;
            p.destroyForcibly();
            if (p.waitFor(5, TimeUnit.SECONDS)) return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        failStop("child pid " + p.pid() + " survived destroyForcibly");
    }

    /** Invariant: the set of live descendants equals the set of registered runs. Anything else is killed. */
    @Scheduled(fixedDelayString = "${prodaccess.runner.reconcile-interval-ms:10000}", initialDelay = 10000)
    public void reconcile() {
        if (!props.runner().reconcile()) return;
        synchronized (lock) {
            ProcessHandle.current().descendants().forEach(ph -> {
                if (registry.containsKey(ph.pid())) return;
                log.error("unregistered child process pid={} cmd={}; killing", ph.pid(), ph.info().command().orElse("?"));
                ph.destroyForcibly();
                try {
                    ph.onExit().get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    failStop("unregistered child pid " + ph.pid() + " could not be killed");
                }
            });
        }
    }

    public int liveRegistered() {
        synchronized (lock) {
            return registry.size();
        }
    }

    /** Last resort when the "no stray process" invariant cannot be restored: let K8s restart the container. */
    void failStop(String reason) {
        log.error("FAIL-STOP: {}", reason);
        Runtime.getRuntime().halt(70);
    }

    // ---- io helpers

    private static class OversizedLine extends IOException {}

    /** Reads one newline-terminated line of bounded size; returns null at EOF. */
    private static byte[] readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(256);
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') return buf.toByteArray();
            if (buf.size() >= MAX_LINE_BYTES) throw new OversizedLine();
            buf.write(b);
        }
        return buf.size() == 0 ? null : buf.toByteArray();
    }

    private static void join(Thread t) {
        try {
            t.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
