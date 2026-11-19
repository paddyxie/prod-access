# Prod Access — project conventions

## Layout

```
common/   io.prodaccess.common.{json,protocol,param,tool}  shared by control and runner; no Spring; tool/ is the API Forge seam
runner/   io.prodaccess.runner[.sandbox]                 the forked child; sandbox/ is QuickJS (WASM on the JVM)
control/  io.prodaccess.control.<feature>               Spring Boot app
web/      src/{api,components,hooks,lib,pages}          React + MUI UI
```

Build is Gradle (`./gradlew build`); versions only in `gradle/libs.versions.toml`, shared settings only in
`buildSrc/src/main/kotlin/prodaccess.java-conventions.gradle.kts`.

## Backend: package by feature

`control` is split by feature, not by layer. A feature package holds its records, service, controller and anything
only it uses: `tool` (the registry), `task`, `run`, `admin`. Cross-cutting pieces have their own package:
`ownership` (CMDB roles and teams), `process` (child process supervision), `security`, `audit`, `config`,
`web` (errors), `jdbc`.

- Controllers are package-private and thin; the actor always comes from `CurrentUser`, never from the request body.
- Records mirror table rows (snake_case columns map to camelCase components); list queries take optional
  filters through `jdbc.SqlWhere`.
- A new feature gets a new package; do not add to a catch-all file.

## UI conventions (enforced by `web/eslint.config.js`; `npm run build` and `./gradlew build` fail on violations)

Every screen is exactly one of three kinds, named after it, and renders through its layout from
`web/src/components/layout`:

| Kind | File name | Layout | Shape |
|---|---|---|---|
| List | `*ListPage.tsx` | `<ListPage>` | header (+ "New ..." top right) → filter bar → table; filters live in the URL query |
| Detail | `*DetailPage.tsx` | `<DetailPage>` | back link → header (+ actions top right) → `<Section>`s |
| Form | `*FormPage.tsx` | `<FormPage>` | back link → header → fields → Cancel / Submit at the bottom |

- Every screen has a URL: `/<section>`, `/<section>/new`, `/<section>/:id`, `/<section>/:id/edit` (see
  `web/src/routes.tsx`). Navigate with the router (`<Link>`, `useNavigate`), never `window.location`.
- `src/pages/` holds only page files. Pages do not use MUI `Table*`, `Drawer` or `AppBar` directly; add or extend a
  layout component instead.
- Backend calls go through `src/api` (no `fetch` elsewhere). Server data is rendered as text: no
  `dangerouslySetInnerHTML`, `innerHTML` or DOM building.
- Scripts are edited with `components/CodeEditor` (Monaco, bundled locally; production cannot reach a CDN).

If a screen needs something the layouts cannot express, change the layout component (so every page gets it),
not the page.
