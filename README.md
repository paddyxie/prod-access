# Prod Access

Web tool for fixing production data with JS: a tool registry per CMDB role, scripts run in a QuickJS sandbox,
access by team ownership of CMDB roles, everything audited. Design doc: https://claude.ai/artifact/Y7iEERgzc4i79QGY795cJj

This is stage 1 of the design (core): tool registry with API Forge and HTTP Client tools, QuickJS runs scripts that
call them, single direct tool calls, team-based access. MCP for agents (stage 2) and mocks (stage 3) are not built yet.

```
common/   protocol (RunSpec, RunEvent, ToolDef), tool clients (Tools), param validation, JSON
runner/   child-process entry: one process = one run; QuickJS (WASM, on the JVM) capability sandbox
control/  Spring Boot app: tool registry, ownership, tasks, runs, queue, supervisor, audit; serves the UI
web/      React + MUI + Monaco single-page UI (built by Gradle into control's static resources)
buildSrc/ shared Gradle conventions; versions live in gradle/libs.versions.toml
```

Code structure and UI conventions: see [CLAUDE.md](CLAUDE.md).

## Build & test

```bash
./gradlew build     # UI type-check + lint + bundle, unit + end-to-end tests (real runner children, mock targets)
```

## Run locally

```bash
./gradlew :control:bootJar :runner:shadowJar
java -jar control/build/libs/control.jar \
  --prodaccess.dev-user=me \
  --prodaccess.api-forge.base-url=http://localhost:9000 \
  --prodaccess.api-forge.token-url=http://localhost:9000/oauth/token \
  --prodaccess.api-forge.client-id=prod-access --prodaccess.api-forge.client-secret=SECRET \
  --prodaccess.cmdb.role-owners.orders=orders-team \
  --prodaccess.cmdb.teams.orders-team=me,alice
```

Then open http://localhost:8080, register tools under a role your team owns, and run a task or call a tool.
For UI work, run `npm run dev` in `web/` (http://localhost:5173, proxies `/api` to :8080).
Dev DB is a local H2 file (`./data`); for Postgres set `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`.
The schema has no migrations yet: after pulling a schema change, delete `./data`.

## Replace-me seams (placeholders in this version)

| Seam | Placeholder | Replace with |
|---|---|---|
| API Forge calls + OAuth | `common/.../tool/Tools` (client_credentials, `baseUrl/<role><path>`) | the API Forge SDK's generic client |
| prod-access credential | `prodaccess.api-forge.client-id/-secret` properties | Vault |
| CMDB (role owners, team members) | `control/.../ownership/ConfigCmdb` (properties) | a client of the real CMDB; implement `Cmdb` |
| Login | `control/.../security/CurrentUser` reads `X-User` / `prodaccess.dev-user` | SSO/OIDC |

## How a run works

1. A script run is submitted with params, validated against the descriptors the script declares. The user's teams
   must own every CMDB role whose tools the script calls; otherwise it is refused (and audited).
2. A dispatcher claims `QUEUED` runs (`FOR UPDATE SKIP LOCKED`), re-checks ownership and that every tool is still
   registered, and forks `runner.jar`.
3. The spec (including the API Forge secret) goes to the child over stdin only. The child exposes
   `api.<role>.<tool>`, `ctx`, `log`, `batch` to the QuickJS sandbox, calls API Forge tools as prod-access and HTTP
   Client tools directly, and emits events on stdout.
4. Supervision: child deadline + stdin-EOF exit; parent deadline/heartbeat/cancel with kill confirmation; reconciler
   kills any descendant not in the registry; fail-stop (process exit) if a kill cannot be confirmed.

A single tool call (`/tools/:id/call`) skips the queue and the child: after the same ownership check the main process
makes the call and records it as a run of kind `CALL`.

## Known gaps vs. the design doc

- Stage 2 (MCP server for agents) and stage 3 (mocks) are not built.
- HTTP Client tools send no credentials: how they authenticate is an open question in the design.
- Task libraries (shared modules) not implemented.
- `batch.each` is sequential (one JS thread per sandbox); `concurrency` is not supported, pace with `rps`.
- Logs are polled (2s), not SSE; no S3 archive; no per-role concurrency cap; no PII filter hook.
- `Dockerfile` is untested (no Docker on this machine).
- The browser always acts as `prodaccess.dev-user` until SSO lands.
- The tool reference analyser is a lexical scan, not an AST pass; it is conservative and the runtime enforces the same set.
