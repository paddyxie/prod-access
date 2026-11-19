package io.prodaccess.runner.sandbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SandboxTest {
    final List<String> calls = new ArrayList<>();
    final List<String> logs = new ArrayList<>();

    final Sandbox.Host host = new Sandbox.Host() {
        public String tools() { return "[\"orders.getOrder\"]"; }
        public String call(String tool, String argsJson) {
            calls.add(tool + " " + argsJson);
            return "{\"ok\":true,\"value\":{\"id\":\"o1\",\"state\":\"FAILED\"}}";
        }
        public void log(String level, String message) { logs.add(level + " " + message); }
        public void step(String name, String state) { logs.add("step " + name + " " + state); }
    };

    static final String TASK = """
            export const meta = { title: 'Look up' };
            export const params = { id: param.string({ label: 'Order id' }) };

            export default async function main(ctx) {
              const o = await ctx.step('lookup', () => api.orders.getOrder({ path: { id: ctx.params.id } }));
              log.info('state', o.state);
              return { id: o.id, state: o.state, run: ctx.runId };
            }
            """;

    @Test
    void inspectReadsMetaAndParamsWithoutRunningMain() {
        try (Sandbox sb = new Sandbox(host)) {
            Sandbox.Outcome out = sb.inspect(TASK);
            assertTrue(out.ok(), out.error());
            assertEquals("{\"meta\":{\"title\":\"Look up\"},\"params\":{\"id\":{\"type\":\"string\",\"label\":\"Order id\"}}}", out.value());
            assertTrue(calls.isEmpty());
        }
    }

    @Test
    void runCallsToolsAndReturnsTheResult() {
        try (Sandbox sb = new Sandbox(host)) {
            Sandbox.Outcome out = sb.run(TASK, "\"r1\"", "{\"id\":\"o1\"}");
            assertTrue(out.ok(), out.error());
            assertEquals("{\"id\":\"o1\",\"state\":\"FAILED\",\"run\":\"r1\"}", out.value());
            assertEquals(List.of("orders.getOrder {\"path\":{\"id\":\"o1\"}}"), calls);
            assertEquals(List.of("step lookup start", "step lookup ok", "info state FAILED"), logs);
        }
    }

    @Test
    void errorsPointAtTheTasksOwnLines() {
        try (Sandbox sb = new Sandbox(host)) {
            Sandbox.Outcome thrown = sb.run("export default async function main() {\n  throw new Error('boom');\n}", "\"r\"", "{}");
            assertFalse(thrown.ok());
            assertTrue(thrown.error().contains("Error: boom") && thrown.error().contains("task.js:2"), thrown.error());
        }
        try (Sandbox sb = new Sandbox(host)) {
            Sandbox.Outcome topLevel = sb.inspect("const x = 1;\nnull.y;");
            assertFalse(topLevel.ok());
            assertTrue(topLevel.error().contains("cannot read property 'y' of null") && topLevel.error().contains("task.js:2"), topLevel.error());
        }
        try (Sandbox sb = new Sandbox(host)) {
            Sandbox.Outcome syntax = sb.inspect("export const meta = {;");
            assertFalse(syntax.ok());
            assertTrue(syntax.error().contains("invalid property name") && syntax.error().contains("task.js:1"), syntax.error());
        }
    }

    @Test
    void missingMainIsReported() {
        try (Sandbox sb = new Sandbox(host)) {
            Sandbox.Outcome out = sb.run("export const meta = {};", "\"r\"", "{}");
            assertFalse(out.ok());
            assertTrue(out.error().contains("default function main"), out.error());
        }
    }

    @Test
    void sandboxHasNoWayOut() {
        try (Sandbox sb = new Sandbox(host)) {
            Sandbox.Outcome out = sb.run("""
                    export default async function main() {
                      return [typeof Javy, typeof eval, typeof __host, typeof __prodaccess, typeof fetch, typeof require,
                              typeof process, typeof setTimeout, typeof std, typeof os, typeof Java].join(',');
                    }
                    """, "\"r\"", "{}");
            assertTrue(out.ok(), out.error());
            assertEquals("\"undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined\"", out.value());
        }
    }
}
