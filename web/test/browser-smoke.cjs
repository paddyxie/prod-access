const assert = require('node:assert/strict');
const { spawn } = require('node:child_process');
const { once } = require('node:events');
const { access } = require('node:fs/promises');
const { createServer } = require('node:http');
const { join, resolve } = require('node:path');
const { setTimeout: delay } = require('node:timers/promises');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

const root = resolve(__dirname, '../..');
const host = '127.0.0.1';
const controlJar = join(root, 'control/build/libs/control.jar');
const runnerJar = join(root, 'runner/build/libs/runner.jar');
const stateSchema = { type: 'object', required: ['state'], properties: { state: { type: 'string' } } };
const orderSchema = { type: 'object', required: ['id'], properties: { id: { type: 'string' } } };
const inputSchema = { type: 'object', required: ['path', 'body'], properties: { path: orderSchema, body: stateSchema } };
const outputSchema = { ...orderSchema, required: ['id', 'state'], properties: { ...orderSchema.properties, ...stateSchema.properties } };
const script = `export const params = { id: param.string({ label: 'Order ID' }) };
export const output = ${JSON.stringify(outputSchema)};
export default async function main(ctx) {
  return await ctx.step('repair order', () => api.orders.repair({
    path: { id: ctx.params.id }, body: { state: 'FIXED' }
  }), { id: ctx.params.id });
}`;

async function choose(page, label, option) {
  await page.getByRole('combobox', { name: label }).click();
  await page.getByRole('option', { name: option, exact: true }).click();
}

async function desktopFlow(page, base, endpoint, received) {
  await page.goto(`${base}/services`);
  await page.getByRole('link', { name: 'New service' }).click();
  await page.getByRole('textbox', { name: 'Service code' }).fill('commerce-smoke');
  await page.getByRole('textbox', { name: /^Name/ }).fill('Commerce Smoke');
  await page.getByRole('button', { name: 'Create service' }).click();
  await page.waitForURL('**/systems/new?serviceId=*');
  await page.getByRole('combobox', { name: /^Service/ }).getByText(/Commerce Smoke/).waitFor();
  await choose(page, 'CMDB Role', 'orders');
  await page.getByRole('textbox', { name: 'System name' }).fill('Orders Web');
  await page.getByRole('button', { name: 'Create system' }).click();
  await page.waitForURL('**/tools?role=orders');

  await page.getByRole('link', { name: 'New tool' }).click();
  await choose(page, /^Role/, 'orders');
  await page.getByRole('textbox', { name: /^Name/ }).fill('repair');
  await choose(page, 'Type', 'HTTP Client');
  await page.getByRole('textbox', { name: 'Base URL' }).fill(endpoint);
  await choose(page, 'Method', 'POST');
  await page.getByRole('textbox', { name: 'Path template' }).fill('/orders/{id}');
  await page.getByRole('textbox', { name: 'Input schema (JSON Schema)' }).fill(JSON.stringify(inputSchema));
  await page.getByRole('textbox', { name: 'Output schema (JSON Schema)' }).fill(JSON.stringify(outputSchema));
  await page.getByRole('button', { name: /^(Save|Create tool)$/ }).click();
  await page.waitForURL(/\/tools\/[0-9a-f-]{36}$/i);
  await page.getByRole('heading', { name: 'API contract' }).waitFor();
  await page.getByRole('link', { name: 'Call tool' }).click();
  await page.getByRole('textbox', { name: /^id\b/ }).fill('direct-order');
  await page.getByRole('textbox', { name: 'Body (JSON)' }).fill('{"state":"FIXED"}');
  await page.getByRole('button', { name: 'Call', exact: true }).click();
  await page.waitForURL('**/runs/*');
  await page.getByText('SUCCEEDED', { exact: true }).first().waitFor();

  await page.getByRole('navigation').getByRole('link', { name: 'Workflows' }).click();
  await page.getByRole('link', { name: 'New workflow' }).click();
  await page.locator('.monaco-editor').click();
  await page.keyboard.press('ControlOrMeta+A');
  await page.keyboard.insertText(script);
  await page.getByRole('textbox', { name: /^Name/ }).fill('Repair order workflow');
  await page.getByRole('button', { name: 'Create workflow' }).click();
  await page.waitForURL(/\/workflows\/[0-9a-f-]{36}$/i);
  await page.getByRole('link', { name: 'Run', exact: true }).click();
  await page.getByRole('textbox', { name: 'Order ID' }).fill('workflow-order');
  await page.getByRole('button', { name: 'Run', exact: true }).click();
  await page.waitForURL('**/runs/*');
  const executionPath = new URL(page.url()).pathname;
  await page.getByText('SUCCEEDED', { exact: true }).first().waitFor({ timeout: 30_000 });
  await page.getByRole('button', { name: /^repair order\b/ }).waitFor();
  const invocation = page.getByRole('button', { name: /commerce-smoke.*orders\.repair.*v1.*alice/i });
  await invocation.click();
  await page.getByText('Attempt 1', { exact: true }).waitFor();
  await page.getByText(`${endpoint}/orders/workflow-order`, { exact: true }).waitFor();
  await page.getByText('Request body', { exact: true }).locator('..').getByText(/FIXED/).waitFor();
  await page.getByText('Response body', { exact: true }).locator('..').getByText(/workflow-order/).waitFor();
  assert.deepEqual(received, [
    { method: 'POST', path: '/orders/direct-order', body: { state: 'FIXED' } },
    { method: 'POST', path: '/orders/workflow-order', body: { state: 'FIXED' } },
  ]);

  await page.getByRole('navigation').getByRole('link', { name: 'Tool audit' }).click();
  const actor = page.getByRole('textbox', { name: 'Actor' });
  await actor.fill('nobody');
  await page.waitForURL((url) => url.searchParams.get('actor') === 'nobody');
  await page.getByText('No tool invocations match.').waitFor();
  await actor.fill('alice');
  await page.waitForURL((url) => url.searchParams.get('actor') === 'alice');
  await page.getByRole('textbox', { name: 'Tool name' }).fill('repair');
  await page.waitForURL((url) => url.searchParams.get('actor') === 'alice' && url.searchParams.get('tool') === 'repair');
  const workflowAudit = page.getByRole('row').filter({ hasText: executionPath.split('/').at(-1) });
  await workflowAudit.getByRole('cell', { name: 'repair v1' }).waitFor();
  await workflowAudit.getByRole('link').first().click();
  await page.waitForURL((url) => url.pathname === executionPath);
  await page.getByText(`${endpoint}/orders/workflow-order`, { exact: true }).waitFor();
}

async function mobileFlow(page, base) {
  await page.goto(`${base}/runs`);
  await page.getByRole('button', { name: 'Open navigation' }).click();
  await page.getByRole('button', { name: 'Use light theme' }).waitFor();
  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'Services' }).click();
  await page.waitForURL('**/services');
  await page.getByRole('navigation', { name: 'Main navigation' }).waitFor({ state: 'hidden' });
  await page.getByRole('heading', { name: 'Services' }).waitFor();
  await page.getByRole('cell', { name: 'Commerce Smoke' }).waitFor();
}

async function main() {
  await Promise.all([access(controlJar), access(runnerJar)]);
  const received = [];
  const target = createServer(async (request, response) => {
    try {
      const chunks = [];
      for await (const chunk of request) chunks.push(chunk);
      const body = JSON.parse(Buffer.concat(chunks).toString());
      received.push({ method: request.method, path: request.url, body });
      response.writeHead(200, { 'Content-Type': 'application/json' });
      response.end(JSON.stringify({ id: request.url.split('/').at(-1), state: body.state }));
    } catch {
      response.writeHead(400).end();
    }
  });
  target.listen(0, host);
  await once(target, 'listening');
  let browser;
  let appError;
  let logs = '';
  const app = spawn('java', [
    '-jar', controlJar,
    `--server.address=${host}`,
    '--server.port=0',
    '--spring.datasource.url=jdbc:h2:mem:browser-smoke;MODE=PostgreSQL;DB_CLOSE_DELAY=-1',
    `--prodaccess.runner.jar=${runnerJar}`,
    '--prodaccess.dev-user=alice',
    '--prodaccess.cmdb.role-owners.orders[0]=orders-team',
    '--prodaccess.cmdb.teams.orders-team[0]=alice',
  ], { cwd: root, stdio: ['ignore', 'pipe', 'pipe'] });
  app.on('error', (error) => { appError = error; });
  app.stdout.on('data', (chunk) => { logs += chunk; });
  app.stderr.on('data', (chunk) => { logs += chunk; });
  try {
    const deadline = Date.now() + 30_000;
    const started = /Tomcat started on port (\d+)/;
    while (!started.test(logs)) {
      if (appError) throw appError;
      assert(app.exitCode === null && Date.now() < deadline, 'The smoke-test app did not start');
      await delay(100);
    }
    const base = `http://${host}:${logs.match(started)[1]}`;
    browser = await chromium.launch({ executablePath: process.env.CHROMIUM_EXECUTABLE || undefined });
    const desktop = await browser.newPage({ viewport: { width: 1440, height: 1000 }, colorScheme: 'light' });
    const mobile = await browser.newPage({ viewport: { width: 390, height: 844 }, colorScheme: 'dark' });
    const errors = [];
    for (const page of [desktop, mobile]) {
      page.setDefaultTimeout(15_000);
      page.on('pageerror', (error) => errors.push(error.message));
    }
    await desktopFlow(desktop, base, `http://${host}:${target.address().port}`, received);
    await mobileFlow(mobile, base);
    assert.deepEqual(errors, [], 'No uncaught browser errors');
    console.log('Browser smoke passed: catalog, tool contracts, direct call, workflow execution, HTTP audit, and mobile navigation.');
  } catch (error) {
    console.error(logs.trim().split('\n').slice(-30).join('\n'));
    throw error;
  } finally {
    await browser?.close();
    app.kill('SIGTERM');
    target.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
