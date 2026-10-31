package io.prodaccess.control.task;

import io.prodaccess.common.protocol.RunSpec;
import io.prodaccess.control.process.ChildProcessRunner;
import io.prodaccess.control.web.ApiException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Extracts {@code meta} and {@code params} by evaluating the module's top level in a runner child with no
 * capabilities (supervised exactly like a real run), so user code never executes inside this JVM.
 */
@Component
public class TaskInspector {
    public record Inspection(Map<String, Object> meta, Map<String, Object> params) {}

    private final ChildProcessRunner runner;

    TaskInspector(ChildProcessRunner runner) {
        this.runner = runner;
    }

    @SuppressWarnings("unchecked")
    public Inspection inspect(String script) {
        var spec = new RunSpec(RunSpec.CURRENT_VERSION, UUID.randomUUID().toString(), RunSpec.Mode.INSPECT, "inspector",
                script, Map.of(), List.of(), new RunSpec.Limits(10_000, 0, 0, 0, 0), null, null);
        var found = new AtomicReference<Map<String, Object>>();
        var outcome = runner.execute(spec, ev -> {
            if (ev.t().equals("inspect")) found.set((Map<String, Object>) ev.value());
        }, () -> false, () -> { });
        if (found.get() == null) {
            throw ApiException.bad("script cannot be loaded: " + outcome.error());
        }
        Map<String, Object> meta = found.get().get("meta") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Map<String, Object> params = found.get().get("params") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        return new Inspection(meta, params);
    }
}
