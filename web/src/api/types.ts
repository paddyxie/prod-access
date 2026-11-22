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
  cmdbRoleId: string;
  service: string;
  versionId: string;
  ver: number;
  retired: boolean;
  role: string;
  name: string;
  type: ToolType;
  method: string;
  pathTemplate: string;
  /** HTTP Client tools only. */
  baseUrl: string | null;
  description: string | null;
  gatewayApiId: string | null;
  inputSchemaJson: string;
  outputSchemaJson: string;
  updatedBy: string;
  updatedAt: number;
}

export interface Workflow {
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

export interface ToolBinding {
  toolId: string;
  versionId: string;
  key: string;
  ver: number;
  retired: boolean;
}

export interface WorkflowVersion {
  id: string;
  workflowId: string;
  ver: number;
  script: string;
  scriptHash: string;
  metaJson: string;
  paramsJson: string;
  outputSchemaJson: string;
  toolsJson: string;
  toolBindings: ToolBinding[];
  createdBy: string;
  createdAt: number;
}

export interface Run {
  id: string;
  kind: RunKind;
  workflowVersionId: string | null;
  toolVersionId: string | null;
  workflowId: string | null;
  workflowName: string | null;
  ver: number | null;
  /** `role.name` of the tool, for a direct call. */
  toolKey: string | null;
  rolesJson: string;
  /** Workflow parameters, or the request of a direct call. */
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
  id: string;
  executionId: string;
  actionId: string | null;
  toolVersionId: string;
  service: string;
  role: string;
  tool: string;
  ver: number;
  type: ToolType;
  actor: string;
  startedAt: number;
  finishedAt: number | null;
  status: string;
  inputJson: string;
  inputTruncated: boolean;
  outputJson: string | null;
  outputTruncated: boolean;
  error: string | null;
}
export interface ApiCallRecord {
  id: string;
  invocationId: string;
  attempt: number;
  startedAt: number;
  finishedAt: number | null;
  method: string;
  url: string;
  requestHeadersJson: string;
  requestBody: string | null;
  requestTruncated: boolean;
  responseStatus: number | null;
  responseHeadersJson: string | null;
  responseBody: string | null;
  responseTruncated: boolean;
  status: string;
  error: string | null;
}
export interface ExecutionAction {
  id: string;
  executionId: string;
  parentActionId: string | null;
  name: string;
  status: string;
  startedAt: number;
  finishedAt: number | null;
  inputJson: string | null;
  resultJson: string | null;
  error: string | null;
}
export interface ServiceDefinition {
  id: string;
  code: string;
  name: string;
  description: string | null;
}
export const SYSTEM_TYPES = ['WEB', 'KAFKA', 'BATCH', 'WORKER', 'OTHER'] as const;
export interface CmdbRole {
  id: string;
  serviceId: string;
  serviceCode: string;
  code: string;
  name: string;
  systemType: (typeof SYSTEM_TYPES)[number];
  description: string | null;
}

/** A param descriptor as declared by a workflow script (`param.string({...})` etc.). */
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

export interface LegacyRecord { sourceTable: string; sourceId: string; executionId: string | null; sourceJson: string; archivedAt: number }

export interface ExecutionInput { executionId: string; submittedJson: string; resolvedJson: string }
