package io.prodaccess.control.run;

import io.prodaccess.common.json.Json;
import io.prodaccess.common.protocol.RunEvent;
import io.prodaccess.common.protocol.RunSpec;
import io.prodaccess.common.protocol.ToolDef;
import io.prodaccess.control.config.ProdAccessProperties;
import io.prodaccess.control.ownership.OwnershipService;
import io.prodaccess.control.process.ChildProcessRunner.Outcome;
import io.prodaccess.control.process.ChildProcessRunner;
import io.prodaccess.control.workflow.WorkflowService;
import io.prodaccess.control.workflow.WorkflowVersion;
import io.prodaccess.control.tool.Tool;
import io.prodaccess.control.tool.ToolService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Claims QUEUED runs from the database (FOR UPDATE SKIP LOCKED, so any number of Pods can share the queue),
 * executes each in a supervised child process, records events and audit, and settles the final state.
 */
@Component
public class RunDispatcher {
    private static final Logger log = LoggerFactory.getLogger(RunDispatcher.class);
    private static final Pattern DURATION = Pattern.compile("(\\d+)\\s*(ms|s|m|h)");

    private final JdbcClient db;
    private final TransactionTemplate tx;
    private final ProdAccessProperties props;
    private final ChildProcessRunner runner;
    private final RunService runs;
    private final WorkflowService workflows;
    private final ToolService tools;
    private final OwnershipService ownership;
    private final ExecutionAudit evidence;
    private final String node = "node-" + UUID.randomUUID().toString().substring(0, 8);
    private final AtomicInteger active = new AtomicInteger();

    RunDispatcher(JdbcClient db, TransactionTemplate tx, ProdAccessProperties props, ChildProcessRunner runner,
                  RunService runs, WorkflowService workflows, ToolService tools, OwnershipService ownership, ExecutionAudit evidence) {
        this.db = db;
        this.tx = tx;
        this.props = props;
        this.runner = runner;
        this.runs = runs;
        this.workflows = workflows;
        this.tools = tools;
        this.ownership = ownership;
        this.evidence = evidence;
    }

    public boolean paused() {
        return db.sql("SELECT v FROM settings WHERE k = 'paused'").query(String.class).optional().map("true"::equals).orElse(false);
    }

    public void setPaused(boolean paused) {
        if (db.sql("UPDATE settings SET v = ? WHERE k = 'paused'").param(String.valueOf(paused)).update() == 0) {
            db.sql("INSERT INTO settings (k, v) VALUES ('paused', ?)").param(String.valueOf(paused)).update();
        }
    }

    public int activeRuns() {
        return active.get();
    }

    @Scheduled(fixedDelayString = "${prodaccess.runner.poll-ms:500}")
    void poll() {
        try {
            if (paused()) return;
            while (active.get() < props.runner().maxConcurrent()) {
                Optional<String> id = claim();
                if (id.isEmpty()) return;
                active.incrementAndGet();
                Thread.ofVirtual().name("run-" + id.get()).start(() -> {
                    try {
                        execute(id.get());
                    } catch (RuntimeException e) {
                        log.error("run {} crashed in dispatcher", id.get(), e);
                        finish(id.get(), RunStatus.FAILED, null, "internal error: " + e.getMessage());
                    } finally {
                        active.decrementAndGet();
                    }
                });
            }
        } catch (RuntimeException e) {
            log.error("dispatch poll failed", e);
        }
    }

    private Optional<String> claim() {
        return tx.execute(s -> {
            Optional<String> id = db.sql("SELECT id FROM executions WHERE status = 'QUEUED' ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED")
                    .query(String.class).optional();
            id.ifPresent(i -> db.sql("UPDATE executions SET status = 'RUNNING', started_at = ?, heartbeat_at = ?, node = ? WHERE id = ?")
                    .params(System.currentTimeMillis(), System.currentTimeMillis(), node, i).update());
            return id;
        });
    }

    /** Runs whose owner stopped heartbeating (its Pod died) are marked LOST. Never retried automatically. */
    @Scheduled(fixedDelayString = "${prodaccess.runner.sweep-ms:10000}", initialDelay = 5000)
    void sweepLost() {
        long now = System.currentTimeMillis();
        long cutoff = now - props.runner().lostAfterMs();
        List<String> candidates = db.sql("SELECT id FROM executions WHERE status = 'RUNNING' AND heartbeat_at < ?")
                .param(cutoff).query(String.class).list();
        for (String id : candidates) {
            // A concurrent heartbeat or completion may have made this candidate current again.
            tx.executeWithoutResult(s -> {
                int updated = db.sql("""
                        UPDATE executions SET status = 'LOST', finished_at = ?, error = 'lost: owner stopped heartbeating'
                        WHERE id = ? AND status = 'RUNNING' AND heartbeat_at < ?""")
                        .params(now, id, cutoff).update();
                if (updated > 0) evidence.closeIncomplete(id, "execution owner lost; remote outcome is unknown");
            });
        }
    }

    // ---- execution

    @SuppressWarnings("unchecked")
    void execute(String runId) {
        Run run = runs.get(runId);
        WorkflowVersion v = workflows.versionById(run.workflowVersionId());

        // Re-check at the last moment: ownership and the registry may have changed while the run was queued.
        Set<String> notOwned = ownership.notOwned(run.submittedBy(), v.roles());
        if (!notOwned.isEmpty()) {
            finish(runId, RunStatus.FAILED, null, "ownership lost while queued: none of your teams owns " + String.join(", ", notOwned));
            return;
        }
        List<ToolDef> defs = new ArrayList<>();
        for (Tool t : tools.forWorkflowVersion(v.id())) {
            if (t.retired()) { finish(runId, RunStatus.FAILED, null, "tool " + t.key() + " was removed from the registry (retired)"); return; }
            defs.add(t.def());
        }

        Map<String, Object> meta = (Map<String, Object>) Json.readTree(v.metaJson());
        var spec = new RunSpec(RunSpec.CURRENT_VERSION, runId, RunSpec.Mode.RUN, run.submittedBy(), v.script(),
                (Map<String, Object>) Json.readTree(run.paramsJson()), defs, limits(meta), props.apiForge().toSpec());

        var logBytes = new AtomicLong();
        var truncated = new AtomicBoolean();
        long[] lastCancelCheck = {0};
        boolean[] cancel = {false};
        Outcome outcome = runner.execute(spec, ev -> record(runId, ev, logBytes, truncated, props.limits().maxLogBytes()),
                () -> {
                    long now = System.currentTimeMillis();
                    if (!cancel[0] && now - lastCancelCheck[0] > 1000) {
                        lastCancelCheck[0] = now;
                        cancel[0] = db.sql("SELECT cancel_requested FROM executions WHERE id = ?").param(runId).query(Boolean.class).single();
                    }
                    return cancel[0];
                },
                () -> db.sql("UPDATE executions SET heartbeat_at = ? WHERE id = ? AND status = 'RUNNING'")
                        .params(System.currentTimeMillis(), runId).update());

        RunStatus status = RunStatus.valueOf(outcome.status().name());
        String error = outcome.error();
        if (status == RunStatus.SUCCEEDED && outcome.resultJson() != null) {
            try { io.prodaccess.common.schema.Schema.validate(v.outputSchemaJson(), Json.readTree(outcome.resultJson()), "workflow output"); }
            catch (IllegalArgumentException e) { status = RunStatus.FAILED; error = e.getMessage(); }
        }
        finish(runId, status, outcome.resultJson(), error);
    }

    private void record(String runId, RunEvent ev, AtomicLong logBytes, AtomicBoolean truncated, int maxLogBytes) {
        if (evidence.record(runId, ev)) return;
        switch (ev.t()) {
            case "log", "step", "error" -> {
                String msg = ev.t().equals("step") ? ev.name() + " " + ev.state() : ev.msg();
                if (logBytes.addAndGet(msg == null ? 0 : msg.length()) > maxLogBytes) {
                    if (truncated.compareAndSet(false, true)) insertEvent(runId, ev.ts(), "log", "warn", "log output truncated");
                    return;
                }
                insertEvent(runId, ev.ts(), ev.t(), ev.level(), msg);
            }
            default -> { }
        }
    }

    private void insertEvent(String runId, long ts, String kind, String lvl, String message) {
        db.sql("INSERT INTO execution_events (execution_id, ts, kind, lvl, message) VALUES (?,?,?,?,?)").params(runId, ts, kind, lvl, message).update();
    }

    private void finish(String runId, RunStatus status, String resultJson, String error) {
        if (!runs.finish(runId, status, resultJson, error)) log.warn("run {} was no longer RUNNING when it finished as {} (swept as LOST?)", runId, status);
    }

    /** Ceilings from configuration; a workflow's meta may only lower them. */
    private RunSpec.Limits limits(Map<String, Object> meta) {
        ProdAccessProperties.Limits cap = props.limits();
        long timeout = cap.timeoutMs();
        if (meta.get("timeout") instanceof String s) timeout = Math.min(timeout, parseDuration(s));
        double rps = meta.get("rps") instanceof Number n ? Math.min(cap.rps(), n.doubleValue()) : cap.rps();
        int calls = meta.get("maxApiCalls") instanceof Number n ? Math.min(cap.maxApiCalls(), n.intValue()) : cap.maxApiCalls();
        return new RunSpec.Limits(timeout, calls, rps, cap.maxConsecutiveFailures(), cap.maxResultBytes());
    }

    static long parseDuration(String s) {
        Matcher m = DURATION.matcher(s.trim());
        if (!m.matches()) throw new IllegalArgumentException("bad duration '" + s + "' (use e.g. 30s, 10m, 1h)");
        long n = Long.parseLong(m.group(1));
        return switch (m.group(2)) {
            case "ms" -> n;
            case "s" -> n * 1000;
            case "m" -> n * 60_000;
            default -> n * 3_600_000;
        };
    }
}
