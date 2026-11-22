# Browser smoke test

Run from the repository root with the project's Java and Node versions. The setup installs Playwright locally without changing dependency manifests.

```sh
./gradlew :control:bootJar :runner:shadowJar
npm --prefix web install --no-save --package-lock=false playwright
(cd web && npx --no-install playwright install chromium)
node web/test/browser-smoke.cjs
```

If you already have Playwright installed, set `PLAYWRIGHT_MODULE` to its absolute package path. Use `CHROMIUM_EXECUTABLE` to point Playwright at an existing Chromium browser.

The test starts the packaged app on a loopback port with an in-memory H2 database and a local HTTP target. It creates a service, system, Tool and Workflow through the desktop UI, checks the requests actually received by the target, follows a filtered audit result to its HTTP evidence, and navigates the app at mobile width in dark mode.
