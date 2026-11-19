// Shapes of the control app's REST API (records under io.prodaccess.control.*).

export const TOOL_TYPES = ['API_FORGE', 'HTTP_CLIENT'] as const;
export type ToolType = (typeof TOOL_TYPES)[number];

export const RUN_STATUSES = ['QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMED_OUT', 'LOST'] as const;
export type RunStatus = (typeof RUN_STATUSES)[number];

export const ACTIVE_STATUSES: readonly RunStatus[] = ['QUEUED', 'RUNNING'];

export type RunKind = 'SCRIPT' | 'CALL';

export interface Me {
  user: string;
  teams: string[];
  /** CMDB roles owned by any of the user's teams: their tools are the ones the user may call. */
  ownedRoles: string[];
}

export interface Tool {
  id: string;
  role: string;
  name: string;
  type: ToolType;
  method: string;
  pathTemplate: string;
  /** HTTP Client tools only. */
  baseUrl: string | null;
  description: string | null;
  updatedBy: string;
  updatedAt: number;
}

export interface Task {
  id: string;
  name: string | null;
  createdBy: string;
  createdAt: number;
  latestVersion: number;
  tools: string[];
  roles: string[];
  updatedBy: string;
  updatedAt: number;
}

export interface TaskVersion {
  id: string;
  taskId: string;
  ver: number;
  script: string;
  scriptHash: string;
  metaJson: string;
  paramsJson: string;
  toolsJson: string;
  createdBy: string;
  createdAt: number;
}

export interface Run {
  id: string;
  kind: RunKind;
  taskId: string | null;
  taskName: string | null;
  ver: number | null;
  /** `role.name` of the tool, for a direct call. */
  toolKey: string | null;
  rolesJson: string;
  /** Task parameters, or the request of a direct call. */
  paramsJson: string;
  submittedBy: string;
  status: RunStatus;
  createdAt: number;
  startedAt: number | null;
  finishedAt: number | null;
  cancelRequested: boolean;
  resultJson: string | null;
  error: string | null;
}

export interface RunLogEntry {
  id: number;
  ts: number;
  kind: 'step' | 'log' | 'error';
  lvl: string | null;
  message: string | null;
}

export interface ToolCallRecord {
  id: number;
  ts: number;
  role: string;
  tool: string;
  type: ToolType;
  method: string;
  path: string;
  status: number | null;
  durationMs: number | null;
  error: string | null;
}

/** A param descriptor as declared by a task script (`param.string({...})` etc.). */
export interface ParamDef {
  type: 'string' | 'text' | 'number' | 'boolean' | 'date' | 'datetime' | 'enum' | 'json' | 'list' | 'csv';
  label?: string;
  description?: string;
  required?: boolean;
  default?: unknown;
  options?: unknown[];
  item?: ParamDef;
  columns?: string[];
}
