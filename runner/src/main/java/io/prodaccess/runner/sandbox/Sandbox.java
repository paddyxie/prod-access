package io.prodaccess.runner.sandbox;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

/**
 * Capability-based JS sandbox. The guest gets no host classes, no real file system, no threads, no
 * environment and no network: the only way out is the host object handed to the prelude, whose functions
 * the prelude wraps as {@code api}, {@code log}, {@code ctx.step}.
 */
public final class Sandbox implements AutoCloseable {
    private static final String TASK = "/task.mjs";
    private static final String ENTRY = "/entry.mjs";
    private static final String PRELUDE = readResource("prelude.js");

    private final Context context;
    private final Value prelude;

    /** @param host capabilities by name; {@code ops} returns the JSON list of callable operations. */
    public Sandbox(String script, Map<String, ProxyExecutable> host, OutputStream guestOut) {
        var fs = new VirtualFileSystem(Map.of(
                TASK, script,
                ENTRY, "import * as m from './task.mjs'; globalThis.__task = m;"));
        this.context = Context.newBuilder("js")
                .allowAllAccess(false)
                .allowHostAccess(HostAccess.NONE)
                .allowHostClassLookup(c -> false)
                .allowIO(IOAccess.newBuilder().fileSystem(fs).build())
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .allowNativeAccess(false)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .allowEnvironmentAccess(org.graalvm.polyglot.EnvironmentAccess.NONE)
                .out(guestOut)
                .err(guestOut)
                .build();
        this.prelude = context.eval("js", PRELUDE).execute(ProxyObject.fromMap(Map.copyOf(host)));
    }

    /** Evaluates the task module (top level only) and returns its exports object. */
    public Value load() {
        Source entry = Source.newBuilder("js", "import * as m from './task.mjs'; globalThis.__task = m;", ENTRY)
                .mimeType("application/javascript+module").cached(false).buildLiteral();
        context.eval(entry);
        Value bindings = context.getBindings("js");
        Value ns = bindings.getMember("__task");
        bindings.removeMember("__task");
        return ns;
    }

    public String inspect(Value ns) {
        return prelude.getMember("inspect").execute(ns).asString();
    }

    /** Starts main(ctx); every host call is synchronous so the returned handle is settled on return. */
    public Value start(Value ns, String runId, String paramsJson) {
        return prelude.getMember("start").execute(ns, runId, paramsJson);
    }

    public void cancel() {
        context.close(true);
    }

    public static PolyglotException unwrap(RuntimeException e) {
        return e instanceof PolyglotException p ? p : null;
    }

    @Override
    public void close() {
        context.close(true);
    }

    private static String readResource(String name) {
        try (InputStream in = Sandbox.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
