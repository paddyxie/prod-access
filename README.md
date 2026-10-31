# Prod Access

Web tool for running reusable JS tasks against internal APIs through the API Gateway, as a chosen Client
identity, with approval and audit. Design doc: https://claude.ai/artifact/Y7iEERgzc4i79QGY795cJj

```
common/   protocol (RunSpec, RunEvent), param validation, JSON
runner/   child-process entry: one process = one run = one Client identity; GraalJS capability sandbox
control/  Spring Boot app: clients, API list, tasks, runs, approval, queue, supervisor, audit; serves the UI
web/      React + MUI + Monaco single-page UI (built by Gradle into control's static resources)
buildSrc/ shared Gradle conventions; versions live in gradle/libs.versions.toml
```

Code structure and UI conventions: see [CLAUDE.md](CLAUDE.md).

## Build & test

```bash
./gradlew build     # UI type-check + lint + bundle, unit + end-to-end tests (real runner children, mock gateway)
```

## Run locally

```bash
./gradlew :control:bootJar :runner:shadowJar
java -jar control/build/libs/control.jar \
  --prodaccess.dev-user=me \
  --prodaccess.gateway.base-url=http://localhost:9000 \
  --prodaccess.gateway.token-url=http://localhost:9000/oauth/token \
  --prodaccess.credentials.demo.client-id=ID --prodaccess.credentials.demo.client-secret=SECRET
```

Then open http://localhost:8080, add a Client (credential ref `demo`), add APIs, create a task.
For UI work, run `npm run dev` in `web/` (http://localhost:5173, proxies `/api` to :8080).
Dev DB is a local H2 file (`./data`); for Postgres set `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`.

## Replace-me seams (placeholders in this version)

| Seam | Placeholder | Replace with |
|---|---|---|
| Gateway calls + OAuth | `runner/.../gateway/SimpleGatewayClient` (client_credentials, `baseUrl/<service><path>`) | client built on the real Gateway SDK; implement `GatewayClient` |
| Client credentials | `control/.../client/ConfigCredentialProvider` (properties) | Vault-backed `CredentialProvider` |
| Login | `control/.../security/CurrentUser` reads `X-User` / `prodaccess.dev-user` | SSO/OIDC; permissions come from the separate authz project |

## How a run works

1. Submit validates params against the descriptors declared in the script; write tasks need the typed client name
   and go to `PENDING_APPROVAL` (approver != submitter; approval is bound to a hash of script version + params + Client + ops).
2. A dispatcher claims `QUEUED` runs (`FOR UPDATE SKIP LOCKED`), re-verifies the hash, and forks `runner.jar`.
3. The spec (including the Client secret) goes to the child over stdin only. The child initialises the Gateway
   client, exposes `api.<service>.<operation>`, `ctx`, `log`, `batch` to the sandbox, and emits events on stdout.
4. Supervision: child deadline + stdin-EOF exit; parent deadline/heartbeat/cancel with kill confirmation; reconciler
   kills any descendant not in the registry; fail-stop (process exit) if a kill cannot be confirmed.

## Known gaps vs. the design doc

- Task libraries (shared modules) not implemented; the module loader (`VirtualFileSystem`) is the extension point.
- `batch.each` is sequential (one JS thread per sandbox); `concurrency` is not supported, pace with `rps`.
- Logs are polled (2s), not SSE; no S3 archive; no per-Client concurrency cap; no PII filter hook.
- JS engine is GraalJS (interpreter mode on stock JDK); the QuickJS-WASM comparison from M0 was not done.
- `Dockerfile` is untested (no Docker on this machine).
- The browser always acts as `prodaccess.dev-user`; approving needs a second identity (`X-User` header) until SSO lands.
- The ops analyser is a lexical scan, not an AST pass; it is conservative and the runtime enforces the same set.
