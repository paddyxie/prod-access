// Runs ahead of the task module, in the same QuickJS module. Takes the host capabilities, removes every
// other way out of the sandbox, and exposes only the intended globals: param, api, log, console, batch,
// ApiError. The suffix the host appends after the task hands its exports to __prodaccess.
(function (host) {
  'use strict';
  const g = globalThis;
  delete g.__host;
  delete g.Javy; // Javy's file-descriptor IO
  delete g.eval;

  const str = (v) => {
    if (typeof v === 'string') return v;
    try { const s = JSON.stringify(v); return s === undefined ? String(v) : s; } catch (e) { return String(v); }
  };
  const fmt = (args) => args.map(str).join(' ');
  const describe = (e) => (e && e.stack ? String(e) + '\n' + e.stack : String(e));

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
  for (const key of JSON.parse(host.tools())) {
    const [role, name] = key.split('.');
    (api[role] || (api[role] = {}))[name] = (args) => {
      const r = JSON.parse(host.call(key, JSON.stringify(args === undefined ? {} : args)));
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

  Object.defineProperty(g, '__prodaccess', {
    configurable: true,
    value: {
      inspect: (meta, params) => host.result(JSON.stringify({ meta: meta === undefined ? null : meta, params: params === undefined ? {} : params })),
      run: (main, runId, params) => {
        if (typeof main !== 'function') return host.fail('task module must export a default function main(ctx)');
        Promise.resolve()
          .then(() => main(ctx(runId, params)))
          .then((v) => host.result(v === undefined ? 'null' : JSON.stringify(v)), (e) => host.fail(describe(e)));
      },
    },
  });
})(globalThis.__host);
