// Shapes of the control app's REST API (records under io.prodaccess.control.*).

export type Access = 'READ' | 'WRITE';

export const RUN_STATUSES = [
  'PENDING_APPROVAL', 'QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMED_OUT', 'REJECTED', 'LOST',
] as const;
export type RunStatus = (typeof RUN_STATUSES)[number];

export const ACTIVE_STATUSES: readonly RunStatus[] = ['PENDING_APPROVAL', 'QUEUED', 'RUNNING'];

export interface Client {
  id: string;
  name: string;
  description: string | null;
  environment: string;
  credentialRef: string;
  enabled: boolean;
  createdBy: string;
  createdAt: number;
}

export interface ApiEntry {
  id: string;
  service: string;
  operation: string;
  method: string;
  pathTemplate: string;
  access: Access;
  updatedBy: string;
  updatedAt: number;
}

export interface Task {
  id: string;
  name: string | null;
  createdBy: string;
  createdAt: number;
  latestVersion: number;
  clientId: string;
  clientName: string;
  hasWrite: boolean;
  updatedBy: string;
  updatedAt: number;
}

export interface TaskVersion {
  id: string;
  taskId: string;
  ver: number;
  clientId: string;
  script: string;
  scriptHash: string;
  metaJson: string;
  paramsJson: string;
  opsJson: string;
  createdBy: string;
  createdAt: number;
}

export interface Run {
  id: string;
  taskId: string;
  taskName: string | null;
  ver: number;
  clientId: string;
  clientName: string;
  paramsJson: string;
  submittedBy: string;
  status: RunStatus;
  /** Decided at submission from the API list of that moment; never changes afterwards. */
  requiresApproval: boolean;
  approvedBy: string | null;
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

export interface ApiCallRecord {
  id: number;
  ts: number;
  service: string;
  operation: string;
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
