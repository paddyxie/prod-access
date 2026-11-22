package io.prodaccess.runner.sandbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SandboxTest {
    final List<String> calls = new ArrayList<>();
    final List<String> callActions = new ArrayList<>();
    final List<String> logs = new ArrayList<>();
    final List<String> evidence = new ArrayList<>();

    final Sandbox.Host host = new Sandbox.Host() {
        public String tools() { return "[\"orders.getOrder\"]"; }
        public String call(String tool, String argsJson, String actionId) {
            calls.add(tool + " " + argsJson);
            callActions.add(actionId);
            return "{\"ok\":true,\"value\":{\"id\":\"o1\",\"state\":\"FAILED\"}}";
        }
        public void log(String level, String message) { logs.add(level + " " + message); }
        public String beginStep(String name, String parentId, String inputJson) {
            logs.add("step " + name + " start");
            evidence.add("start " + name + " after " + parentId + ": " + inputJson);
            return name;
        }
        public void endStep(String id, String name, String state, String payloadJson) {
            logs.add("step " + name + " " + state);
            evidence.add("end " + id + " " + state + ": " + payloadJson);
        }
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
            assertEquals("{\"meta\":{\"title\":\"Look up\"},\"params\":{\"id\":{\"type\":\"string\",\"label\":\"Order id\"}},\"output\":{}}", out.value());
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
            assertEquals(List.of("lookup"), callActions);
            assertEquals(List.of("step lookup start", "step lookup ok", "info state FAILED"), logs);
        }
    }

    @Test
    void overlappingStepsKeepTheirOwnCallsParentsAndResults() {
        try (Sandbox sb = new Sandbox(host)) {
            Sandbox.Outcome out = sb.run("""
                    export default async function main(ctx) {
                      let resumeSecond;
                      const waiting = new Promise(resolve => { resumeSecond = resolve; });
                      const first = ctx.step('first', async action => {
                        await Promise.resolve();
                        action.api.orders.getOrder({});
                        return await action.step('nested', child => {
                          child.api.orders.getOrder({});
                          return 'first result';
                        });
                      });
                      const second = ctx.step('second', async action => {
                        await waiting;
                        action.api.orders.getOrder({});
                        return 'second result';
                      });
                      const firstResult = await first;
                      resumeSecond();
                      return [firstResult, await second];
                    }
                    """, "\"r\"", "{}");
            assertTrue(out.ok(), out.error());
            assertEquals("[\"first result\",\"second result\"]", out.value());
            assertEquals(List.of("first", "nested", "second"), callActions);
            assertEquals(List.of("start first after : null", "start second after : null", "start nested after first: null",
                    "end nested ok: \"first result\"", "end first ok: \"first result\"", "end second ok: \"second result\""), evidence);
        }
    }

    @Test
    void overlappingStepsRejectAmbiguousGlobalToolCalls() {
        try (Sandbox sb = new Sandbox(host)) {
            Sandbox.Outcome out = sb.run("""
                    export default async function main(ctx) {
                      const first = ctx.step('first', async () => {
                        await Promise.resolve();
                        return api.orders.getOrder({});
                      });
                      const second = ctx.step('second', () => 'second result');
                      return (await Promise.allSettled([first, second]))[0].reason.message;
                    }
                    """, "\"r\"", "{}");
            assertTrue(out.ok(), out.error());
            assertTrue(out.value().contains("when workflow actions overlap"));
            assertTrue(calls.isEmpty());
            assertTrue(evidence.stream().anyMatch(item -> item.startsWith("end first fail:")));
        }
    }

    @Test
    void errorsPointAtTheWorkflowsOwnLines() {
        try (Sandbox sb = new Sandbox(host)) {
            Sandbox.Outcome thrown = sb.run("export default async function main() {\n  throw new Error('boom');\n}", "\"r\"", "{}");
            assertFalse(thrown.ok());
            assertTrue(thrown.error().contains("Error: boom") && thrown.error().contains("workflow.js:2"), thrown.error());
        }
        try (Sandbox sb = new Sandbox(host)) {
            Sandbox.Outcome topLevel = sb.inspect("const x = 1;\nnull.y;");
            assertFalse(topLevel.ok());
            assertTrue(topLevel.error().contains("cannot read property 'y' of null") && topLevel.error().contains("workflow.js:2"), topLevel.error());
        }
        try (Sandbox sb = new Sandbox(host)) {
            Sandbox.Outcome syntax = sb.inspect("export const meta = {;");
            assertFalse(syntax.ok());
            assertTrue(syntax.error().contains("invalid property name") && syntax.error().contains("workflow.js:1"), syntax.error());
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
