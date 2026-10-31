import type { ApiEntry } from '../api';

/** Type declarations of the runner sandbox's globals (see runner prelude.js), for editor completion and checks. */
export function sandboxDeclarations(apis: ApiEntry[]): string {
  const services = new Map<string, ApiEntry[]>();
  for (const a of apis) services.set(a.service, [...(services.get(a.service) ?? []), a]);
  const api = [...services]
    .map(([service, ops]) => {
      const members = ops
        .map((o) => `    /** ${o.method} ${o.pathTemplate} · ${o.access} */\n    ${o.operation}(args?: ApiArgs): any;`)
        .join('\n');
      return `  readonly ${service}: {\n${members}\n  };`;
    })
    .join('\n');

  return `
interface ParamOptions<T> { label?: string; description?: string; required?: boolean; default?: T }
interface ParamDef { readonly type: string }
declare const param: {
  string(o?: ParamOptions<string> & { maxLength?: number; pattern?: string }): ParamDef;
  text(o?: ParamOptions<string> & { maxLength?: number }): ParamDef;
  number(o?: ParamOptions<number> & { min?: number; max?: number }): ParamDef;
  boolean(o?: ParamOptions<boolean>): ParamDef;
  date(o?: ParamOptions<string>): ParamDef;
  datetime(o?: ParamOptions<string>): ParamDef;
  json(o?: ParamOptions<unknown>): ParamDef;
  csv(o?: ParamOptions<string> & { columns?: string[]; max?: number }): ParamDef;
  enum(options: (string | number)[], o?: ParamOptions<string | number>): ParamDef;
  list(item: ParamDef, o?: ParamOptions<string> & { max?: number }): ParamDef;
};

interface ApiArgs {
  path?: Record<string, string | number>;
  query?: Record<string, string | number | boolean>;
  headers?: Record<string, string>;
  body?: unknown;
}
declare class ApiError extends Error { readonly status?: number; readonly body?: unknown }
/** The operations in the API list. Calls run as the task's Client and are audited. */
declare const api: {
${api}
};

interface Logger { info(...args: unknown[]): void; warn(...args: unknown[]): void; error(...args: unknown[]): void }
declare const log: Logger;
declare const console: Logger & { log(...args: unknown[]): void; debug(...args: unknown[]): void };

declare const batch: {
  /** Sequential; stops at the first failure unless continueOnError. */
  each<T, R>(items: T[], fn: (item: T, index: number) => R | Promise<R>,
             opts?: { continueOnError?: boolean; maxFailures?: number }): Promise<Array<{ item: T; ok: boolean; value?: R; error?: string }>>;
};

interface TaskContext {
  readonly runId: string;
  readonly params: Record<string, any>;
  readonly log: Logger;
  /** A named step: its start and end are recorded in the run log. */
  step<T>(name: string, fn: () => T | Promise<T>): Promise<T>;
}
`;
}
