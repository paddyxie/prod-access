import { schemaType, type JsonSchema } from './jsonSchema';
import type { Tool } from '../api';
import { TOOL_TYPE_LABEL } from '../components/StatusChip';

/** Type declarations of the runner sandbox's globals (see runner prelude.js), for editor completion and checks. */
export function sandboxDeclarations(tools: Tool[]): string {
  const roles = new Map<string, Tool[]>();
  for (const t of tools) roles.set(t.role, [...(roles.get(t.role) ?? []), t]);
  const api = [...roles]
    .map(([role, ts]) => {
      const members = ts
        .map((t) => {
          const doc = [t.description, `${TOOL_TYPE_LABEL[t.type]} · ${t.method} ${t.baseUrl ?? ''}${t.pathTemplate}`].filter(Boolean).join(' — ');
          return `    /** ${doc.replaceAll('*/', '* /')} */\n    ${t.name}(args: ${schemaType(JSON.parse(t.inputSchemaJson) as JsonSchema)}): ${schemaType(JSON.parse(t.outputSchemaJson) as JsonSchema)};`;
        })
        .join('\n');
      return `  readonly ${role}: {\n${members}\n  };`;
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

declare class ApiError extends Error { readonly status?: number; readonly body?: unknown }
/** The registered tools, by provider CMDB role. Every call is checked against the run's tool set and audited. */
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

interface WorkflowContext {
  readonly runId: string;
  readonly params: Record<string, any>;
  readonly log: Logger;
  /** Calls on the step callback's context are attributed to that step, including after await. */
  readonly api: typeof api;
  /** Records a named action. Use the callback context for nested steps and overlapping tool calls. */
  step<T>(name: string, fn: (action: WorkflowContext) => T | Promise<T>, input?: unknown): Promise<T>;
}
`;
}
