package io.prodaccess.control.run;

import io.prodaccess.common.json.Json;
import io.prodaccess.common.protocol.Access;
import io.prodaccess.common.protocol.ApiDef;
import io.prodaccess.common.protocol.RunEvent;
import io.prodaccess.common.protocol.RunSpec;
import io.prodaccess.control.apicatalog.ApiCatalogService;
import io.prodaccess.control.apicatalog.ApiEntry;
import io.prodaccess.control.client.Client;
import io.prodaccess.control.client.ClientService;
import io.prodaccess.control.client.CredentialProvider;
import io.prodaccess.control.config.ProdAccessProperties;
import io.prodaccess.control.process.ChildProcessRunner.Outcome;
import io.prodaccess.control.process.ChildProcessRunner;
import io.prodaccess.control.task.TaskService;
import io.prodaccess.control.task.TaskVersion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final CredentialProvider credentials;
    private final RunService runs;
    private final TaskService tasks;
    private final ClientService clients;
    private final ApiCatalogService catalog;
    private final String node = "node-" + UUID.randomUUID().toString().substring(0, 8);
    private final AtomicInteger active = new AtomicInteger();

    RunDispatcher(JdbcClient db, TransactionTemplate tx, ProdAccessProperties props, ChildProcessRunner runner,
                  CredentialProvider credentials, RunService runs, TaskService tasks, ClientService clients,
                  ApiCatalogService catalog) {
        this.db = db;
        this.tx = tx;
        this.props = props;
        this.runner = runner;
        this.credentials = credentials;
        this.runs = runs;
        this.tasks = tasks;
        this.clients = clients;
        this.catalog = catalog;
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
            Optional<String> id = db.sql("SELECT id FROM runs WHERE status = 'QUEUED' ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED")
                    .query(String.class).optional();
            id.ifPresent(i -> db.sql("UPDATE runs SET status = 'RUNNING', started_at = ?, heartbeat_at = ?, node = ? WHERE id = ?")
                    .params(System.currentTimeMillis(), System.currentTimeMillis(), node, i).update());
            return id;
        });
    }

    /** Runs whose owner stopped heartbeating (its Pod died) are marked LOST. Never retried automatically. */
    @Scheduled(fixedDelayString = "${prodaccess.runner.sweep-ms:10000}", initialDelay = 5000)
    void sweepLost() {
        long now = System.currentTimeMillis();
        int n = db.sql("UPDATE runs SET status = 'LOST', finished_at = ?, error = 'lost: owner stopped heartbeating' "
                        + "WHERE status = 'RUNNING' AND heartbeat_at < ?")
                .params(now, now - props.runner().lostAfterMs()).update();
        if (n > 0) log.warn("marked {} run(s) LOST", n);
    }

    // ---- execution

    @SuppressWarnings("unchecked")
    void execute(String runId) {
        Run run = runs.get(runId);
        TaskVersion v = tasks.version(run.taskId(), run.ver());
        Client client = clients.get(run.clientId());
        Map<String, ApiEntry> known = new HashMap<>();
        catalog.all().forEach(a -> known.put(a.key(), a));

        // Re-check at the last moment: what runs must be exactly what was approved.
        if (!client.enabled()) { finish(runId, RunStatus.FAILED, null, "client '" + client.name() + "' is disabled"); return; }
        if (!RunService.approvalHash(v, run).equals(run.approvalHash())) {
            finish(runId, RunStatus.FAILED, null, "integrity check failed: run content differs from what was approved");
            return;
        }
        List<ApiDef> apis = new ArrayList<>();
        boolean needsApproval = false;
        for (String o : v.ops()) {
            ApiEntry a = known.get(o);
            if (a == null) { finish(runId, RunStatus.FAILED, null, "operation " + o + " was removed from the API list"); return; }
            needsApproval |= a.access().equals(Access.WRITE.name());
            apis.add(new ApiDef(a.service(), a.operation(), a.method(), a.pathTemplate(), Access.valueOf(a.access())));
        }
        if (needsApproval && run.approvedBy() == null) {
            finish(runId, RunStatus.FAILED, null, "operation classification changed to WRITE after submission; resubmit for approval");
            return;
        }

        RunSpec.ClientAuth auth;
        try {
            auth = credentials.resolve(client.credentialRef());
        } catch (RuntimeException e) {
            finish(runId, RunStatus.FAILED, null, "cannot resolve client credentials: " + e.getMessage());
            return;
        }
        Map<String, Object> meta = (Map<String, Object>) Json.readTree(v.metaJson());
        var spec = new RunSpec(RunSpec.CURRENT_VERSION, runId, RunSpec.Mode.RUN, run.submittedBy(), v.script(),
                (Map<String, Object>) Json.readTree(run.paramsJson()), apis, limits(meta), auth,
                new RunSpec.Gateway(props.gateway().baseUrl(), props.gateway().tokenUrl()));

        var logBytes = new AtomicLong();
        var truncated = new AtomicBoolean();
        long[] lastCancelCheck = {0};
        boolean[] cancel = {false};
        Outcome outcome = runner.execute(spec, ev -> record(runId, ev, logBytes, truncated, props.limits().maxLogBytes()),
                () -> {
                    long now = System.currentTimeMillis();
                    if (!cancel[0] && now - lastCancelCheck[0] > 1000) {
                        lastCancelCheck[0] = now;
                        cancel[0] = db.sql("SELECT cancel_requested FROM runs WHERE id = ?").param(runId).query(Boolean.class).single();
                    }
                    return cancel[0];
                },
                () -> db.sql("UPDATE runs SET heartbeat_at = ? WHERE id = ? AND status = 'RUNNING'")
                        .params(System.currentTimeMillis(), runId).update());

        finish(runId, RunStatus.valueOf(outcome.status().name()), outcome.resultJson(), outcome.error());
    }

    private void record(String runId, RunEvent ev, AtomicLong logBytes, AtomicBoolean truncated, int maxLogBytes) {
        switch (ev.t()) {
            case "api" -> db.sql("""
                    INSERT INTO api_call_audit (run_id, ts, service, operation, method, path, status, duration_ms, error)
                    VALUES (?,?,?,?,?,?,?,?,?)""")
                    .params(runId, ev.ts(), ev.service(), ev.operation(), ev.method(), ev.path(), ev.status(), ev.ms(), ev.err()).update();
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
        db.sql("INSERT INTO run_events (run_id, ts, kind, lvl, message) VALUES (?,?,?,?,?)").params(runId, ts, kind, lvl, message).update();
    }

    private void finish(String runId, RunStatus status, String resultJson, String error) {
        int n = db.sql("UPDATE runs SET status = ?, finished_at = ?, result_json = ?, error = ? WHERE id = ? AND status = 'RUNNING'")
                .params(status.name(), System.currentTimeMillis(), resultJson, error, runId).update();
        if (n == 0) log.warn("run {} was no longer RUNNING when it finished as {} (swept as LOST?)", runId, status);
    }

    /** Ceilings from configuration; a task's meta may only lower them. */
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
