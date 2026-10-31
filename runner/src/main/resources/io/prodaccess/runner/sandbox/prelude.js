// Evaluated once per sandbox. Receives the capability object `host` (strings in, strings out)
// and exposes only the intended globals: param, api, ctx (passed to main), log, console, batch.
(function (host) {
  'use strict';
  const g = globalThis;
  const str = (v) => {
    if (typeof v === 'string') return v;
    try { const s = JSON.stringify(v); return s === undefined ? String(v) : s; } catch (e) { return String(v); }
  };
  const fmt = (args) => args.map(str).join(' ');

  const desc = (type, o) => Object.assign({ type }, o || {});
  g.param = {
    string: (o) => desc('string', o),
    text: (o) => desc('text', o),
    number: (o) => desc('number', o),
    boolean: (o) => desc('boolean', o),
    date: (o) => desc('date', o),
    datetime: (o) => desc('datetime', o),
    json: (o) => desc('json', o),
    csv: (o) => desc('csv', o),
    enum: (options, o) => desc('enum', Object.assign({ options }, o || {})),
    list: (item, o) => desc('list', Object.assign({ item }, o || {})),
  };

  const logger = {
    info: (...a) => host.log('info', fmt(a)),
    warn: (...a) => host.log('warn', fmt(a)),
    error: (...a) => host.log('error', fmt(a)),
  };
  g.log = logger;
  g.console = { log: logger.info, info: logger.info, warn: logger.warn, error: logger.error, debug: logger.info };

  class ApiError extends Error {
    constructor(message, status, body) {
      super(message);
      this.name = 'ApiError';
      this.status = status;
      this.body = body;
    }
  }
  g.ApiError = ApiError;

  const api = {};
  for (const op of JSON.parse(host.ops())) {
    const [service, operation] = op.split('.');
    (api[service] || (api[service] = {}))[operation] = (args) => {
      const r = JSON.parse(host.call(service, operation, JSON.stringify(args === undefined ? {} : args)));
      if (r.ok) return r.value;
      throw new ApiError(r.error, r.status, r.body);
    };
  }
  g.api = Object.freeze(api);

  g.batch = {
    // Sequential by design: one JS thread per sandbox; pacing comes from the per-run rps limit.
    each: async (items, fn, opts) => {
      const o = opts || {};
      const results = [];
      let failures = 0;
      for (let i = 0; i < items.length; i++) {
        try {
          results.push({ item: items[i], ok: true, value: await fn(items[i], i) });
        } catch (e) {
          failures++;
          results.push({ item: items[i], ok: false, error: String((e && e.message) || e) });
          if (!o.continueOnError || (o.maxFailures && failures >= o.maxFailures)) break;
        }
      }
      return results;
    },
  };

  const ctx = (runId, params) => Object.freeze({
    runId,
    params,
    log: logger,
    step: async (name, fn) => {
      host.step(name, 'start');
      try {
        const v = await fn();
        host.step(name, 'ok');
        return v;
      } catch (e) {
        host.step(name, 'fail');
        throw e;
      }
    },
  });

  return {
    inspect: (ns) => JSON.stringify({ meta: ns.meta === undefined ? null : ns.meta, params: ns.params === undefined ? {} : ns.params }),
    start: (ns, runId, paramsJson) => {
      const out = { state: 'pending' };
      if (typeof ns.default !== 'function') {
        out.state = 'error';
        out.message = 'task module must export a default function main(ctx)';
        return out;
      }
      Promise.resolve()
        .then(() => ns.default(ctx(runId, JSON.parse(paramsJson))))
        .then(
          (v) => { out.state = 'ok'; out.value = v === undefined ? 'null' : JSON.stringify(v); },
          (e) => { out.state = 'error'; out.message = String((e && e.stack) || e); });
      return out;
    },
  };
})
