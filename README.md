# Prod Access

Web tool for fixing production data with JS: a tool registry per CMDB role, scripts run in a QuickJS sandbox,
access by team ownership of CMDB roles, everything audited.

Domain model: Service → CMDB Role (system) → immutable Tool API contracts. Workflow definitions and versions
are separate from executions, action traces, tool invocations, and individual HTTP attempts. See [ER model](docs/data-model.md). Design doc: https://claude.ai/artifact/Y7iEERgzc4i79QGY795cJj

This is stage 1 of the design (core): tool registry with API Forge and HTTP Client tools, QuickJS runs scripts that
call them, single direct tool calls, team-based access. MCP for agents (stage 2) and mocks (stage 3) are not built yet.

```
common/   protocol (RunSpec, RunEvent, ToolDef), tool clients (Tools), param validation, JSON
runner/   child-process entry: one process = one run; QuickJS (WASM, on the JVM) capability sandbox
control/  Spring Boot app: tool registry, catalog, ownership, workflows, executions, queue, supervisor, audit; serves the UI
web/      React + MUI + Monaco single-page UI (built by Gradle into control's static resources)
buildSrc/ shared Gradle conventions; versions live in gradle/libs.versions.toml
```

Code structure and UI conventions: see [CLAUDE.md](CLAUDE.md).

## Build & test

```bash
./gradlew build     # UI type-check + lint + bundle, unit + end-to-end tests (real runner children, mock targets)
```

The [browser smoke test](web/test/README.md) runs the packaged app through catalog setup, Tool and Workflow
execution, audit lookup, and mobile navigation against isolated local fixtures.

## Run locally

```bash
./gradlew :control:bootRun
```

Open http://localhost:8080. `bootRun` defaults to the `local` Spring profile, which
binds to loopback and signs browser requests in as `local-dev`. The sample team
owns the `orders` Role. Set `SPRING_PROFILES_ACTIVE` to select another profile.

For a local launch from the packaged JAR, opt in to the same profile:

```bash
./gradlew :control:bootJar :runner:shadowJar
java -jar control/build/libs/control.jar --spring.profiles.active=local
```

You can also supply your own development identity and role assignments:

```bash
./gradlew :control:bootJar :runner:shadowJar
java -jar control/build/libs/control.jar \
  --server.address=127.0.0.1 \
  --prodaccess.dev-user=me \
  --prodaccess.api-forge.base-url=http://localhost:9000 \
  --prodaccess.api-forge.token-url=http://localhost:9000/oauth/token \
  --prodaccess.api-forge.client-id=prod-access --prodaccess.api-forge.client-secret=SECRET \
  --prodaccess.cmdb.role-owners.orders=orders-team \
  --prodaccess.cmdb.teams.orders-team=me,alice
```

Then open http://localhost:8080, register a Service and its systems (CMDB Roles), define tools with input/output
schemas under a role your team owns, and execute a Workflow or call a tool. Saving a tool creates an immutable
version; saving a Workflow pins exact tool contract versions.
For UI work, run `npm run dev` in `web/` (http://localhost:5173, proxies `/api` to :8080).
Dev DB is a local H2 file (`./data`); for Postgres set `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`.
Existing databases are preserved: see [upgrade instructions](docs/migration.md). The app detects populated legacy
tables and requires an explicit `--prodaccess.migrate-legacy=true` archive step. Do not delete old audit data.

## Replace-me seams (placeholders in this version)

| Seam | Placeholder | Replace with |
|---|---|---|
| API Forge calls + OAuth | `common/.../tool/Tools` (client_credentials, `baseUrl/<role><path>`) | the API Forge SDK's generic client |
| prod-access credential | `prodaccess.api-forge.client-id/-secret` properties | Vault |
| CMDB (role owners, team members) | `control/.../ownership/ConfigCmdb` (properties) | a client of the real CMDB; implement `Cmdb` |
| Login | `control/.../security/CurrentUser` reads `X-User` / `prodaccess.dev-user` | SSO/OIDC |

## How a run works

1. A Workflow execution is submitted with params, validated against the descriptors the script declares. The user's teams
   must own every CMDB role whose tools the script calls; otherwise it is refused (and audited).
2. A dispatcher claims `QUEUED` runs (`FOR UPDATE SKIP LOCKED`), re-checks ownership and that every pinned tool is still active, and forks `runner.jar`.
3. The spec (including the API Forge secret) goes to the child over stdin only. The child exposes
   `api.<role>.<tool>`, `ctx`, `log`, `batch` to the QuickJS sandbox, calls API Forge tools as prod-access and HTTP
   Client tools directly, and emits events on stdout.
4. Supervision: child deadline + stdin-EOF exit; parent deadline/heartbeat/cancel with kill confirmation; reconciler
   kills any descendant not in the registry; fail-stop (process exit) if a kill cannot be confirmed.

A single tool call (`/tools/:id/call`) skips the queue and the child: after the same ownership check the main process
makes the call and records it as a run of kind `CALL`.

## Execution evidence

Each execution retains submitted and validated inputs, actual output, actor, timing, and status. `ctx.step(name, fn, input)`
creates an Action record with parent, input/output and outcome. If steps overlap, use the callback context `action.api` and `action.step` for explicit attribution. Tool Invocations pin an immutable API contract and
capture input/output; each actual HTTP attempt records its resolved URL, method, request/response, timing and outcome,
including the 401 retry. Starts are persisted and acknowledged by the parent before the runner proceeds. Interrupted
requests without completion evidence remain `UNKNOWN` because the remote operation may already have happened.
Credential headers and common secret fields are redacted in call evidence; large payloads have explicit truncation markers.

Tool contracts use the supported JSON Schema subset described in the [model](docs/data-model.md), validated in both
execution paths and used for editor argument/return-value completion. A Workflow may declare `export const output = {...}`
for its output contract. API Forge catalog import is still a replacement seam, not an implemented external integration.

## Known gaps vs. the design doc

- Stage 2 (MCP server for agents) and stage 3 (mocks) are not built.
- HTTP Client tools send no credentials: how they authenticate is an open question in the design.
- Workflow libraries (shared modules) not implemented.
- `batch.each` is sequential (one JS thread per sandbox); `concurrency` is not supported, pace with `rps`.
- Logs are polled (2s), not SSE; no S3 archive; no per-role concurrency cap; no PII filter hook.
- `Dockerfile` is untested (no Docker on this machine).
- The local profile uses `prodaccess.dev-user` as the browser identity. Other launches expect `X-User` from the authentication proxy unless a development identity is explicitly configured.
- The tool reference analyser is a lexical scan, not an AST pass; it is conservative and the runtime enforces the same set.
