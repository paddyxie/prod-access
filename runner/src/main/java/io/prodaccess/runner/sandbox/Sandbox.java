package io.prodaccess.runner.sandbox;

import io.roastedroot.quickjs4j.core.Builtins;
import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.GuestException;
import io.roastedroot.quickjs4j.core.HostFunction;
import io.roastedroot.quickjs4j.core.Runner;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import run.endive.runtime.ByteArrayMemory;
import run.endive.wasm.types.MemoryLimits;

/**
 * Capability-based JS sandbox on QuickJS, compiled to WASM and run in a pure-JVM WASM runtime. The guest has
 * no file system, network, clock-driven timers or Java access: the only way out is the {@link Host} functions,
 * which the prelude wraps as {@code api}, {@code log} and {@code ctx.step}.
 *
 * <p>The task is one ES module. QuickJS gives the host no handle on its exports, so the host rewrites the
 * module's {@code export default} into a binding and appends a suffix that passes {@code meta}, {@code params}
 * and that binding to the prelude.
 */
public final class Sandbox implements AutoCloseable {

    /** Capabilities handed to the guest. Strings in, strings out. */
    public interface Host {
        /** JSON array of the tool keys this run may call. */
        String tools();

        /** Performs a tool call; returns a JSON envelope {@code {ok, value | error, status, body}}. */
        String call(String tool, String argsJson);

        void log(String level, String message);

        void step(String name, String state);
    }

    /** How the module ended: {@code value} is JSON text when {@code ok}, else {@code error} explains. */
    public record Outcome(boolean ok, String value, String error) {}

    private static final String PRELUDE = readResource("prelude.js");
    private static final int MAX_MEMORY_PAGES = 4096; // 64 KiB pages: 256 MiB of guest memory
    private static final Pattern DEFAULT_EXPORT = Pattern.compile("(?m)^([ \\t]*)export[ \\t]+default[ \\t]+");
    private static final Pattern POSITION = Pattern.compile("function\\.mjs:(\\d+)");
    private static final Pattern PANIC = Pattern.compile("`Err` value: (.*?)\\s*(?:note: run with|$)", Pattern.DOTALL);

    private final Runner runner;
    private final int lineOffset;
    private volatile String result;
    private volatile String failure;

    public Sandbox(Host host) {
        Builtins builtins = Builtins.builder("__host")
                .add(new HostFunction("tools", List.of(), String.class, a -> host.tools()))
                .add(new HostFunction("call", List.of(String.class, String.class), String.class,
                        a -> host.call((String) a.get(0), (String) a.get(1))))
                .add(new HostFunction("log", List.of(String.class, String.class), Void.class, a -> {
                    host.log((String) a.get(0), (String) a.get(1));
                    return null;
                }))
                .add(new HostFunction("step", List.of(String.class, String.class), Void.class, a -> {
                    host.step((String) a.get(0), (String) a.get(1));
                    return null;
                }))
                .add(new HostFunction("result", List.of(String.class), Void.class, a -> {
                    result = (String) a.get(0);
                    return null;
                }))
                .add(new HostFunction("fail", List.of(String.class), Void.class, a -> {
                    failure = (String) a.get(0);
                    return null;
                }))
                .build();
        Engine engine = Engine.builder()
                .addBuiltins(builtins)
                .withMemoryFactory(l -> new ByteArrayMemory(new MemoryLimits(l.initialPages(), MAX_MEMORY_PAGES)))
                .build();
        this.runner = Runner.builder().withEngine(engine).build();
        this.lineOffset = runner.jsPreludeLineCount() + (int) PRELUDE.chars().filter(c -> c == '\n').count();
    }

    /** Evaluates the module's top level only and returns {@code {meta, params}} as JSON. */
    public Outcome inspect(String script) {
        return exec(script, "__prodaccess.inspect(typeof meta === 'undefined' ? undefined : meta,"
                + " typeof params === 'undefined' ? undefined : params);");
    }

    /** Evaluates the module and runs {@code main(ctx)} to completion; every host call is synchronous. */
    public Outcome run(String script, String runIdJson, String paramsJson) {
        return exec(script, "__prodaccess.run(typeof __main === 'undefined' ? undefined : __main, " + runIdJson + ", " + paramsJson + ");");
    }

    private Outcome exec(String script, String driver) {
        Matcher m = DEFAULT_EXPORT.matcher(script);
        String module = m.find() ? m.replaceFirst("$1const __main = ") : script;
        try {
            runner.compileAndExec(PRELUDE + module + "\n;{ const __pa = globalThis.__prodaccess; delete globalThis.__prodaccess; "
                    + driver.replace("__prodaccess.", "__pa.") + " }\n");
        } catch (GuestException | IllegalArgumentException e) {
            return new Outcome(false, null, describe(e.getMessage()));
        }
        if (failure != null) return new Outcome(false, null, positions(failure));
        if (result != null) return new Outcome(true, result, null);
        return new Outcome(false, null, "task did not settle: main(ctx) is still waiting on a promise that nothing resolves");
    }

    /** Stops a running script: interrupts the engine thread. */
    public void cancel() {
        runner.close();
    }

    @Override
    public void close() {
        runner.close();
    }

    /** Keeps only the guest's own error from an engine message, with lines counted from the task's first line. */
    private String describe(String message) {
        String m = message == null ? "" : message;
        int err = m.indexOf("stderr:");
        if (err >= 0) {
            int out = m.indexOf("\nstdout:", err);
            m = m.substring(err + "stderr:".length(), out < 0 ? m.length() : out);
        }
        Matcher panic = PANIC.matcher(m);
        if (panic.find()) m = panic.group(1);
        return positions(m.strip());
    }

    private String positions(String text) {
        return POSITION.matcher(text).replaceAll(r -> "task.js:" + Math.max(1, Integer.parseInt(r.group(1)) - lineOffset));
    }

    private static String readResource(String name) {
        try (InputStream in = Sandbox.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
