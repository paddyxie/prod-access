import { http, type Query } from './http';
import type { Me, Run, RunLogEntry, Workflow, WorkflowVersion, Tool, ToolCallRecord, ApiCallRecord, ExecutionAction, ServiceDefinition, CmdbRole, LegacyRecord, ExecutionInput } from './types';

export { ApiError } from './http';
export * from './types';

export type ToolInput = Pick<Tool, 'role' | 'name' | 'type' | 'method' | 'pathTemplate' | 'baseUrl' | 'description' | 'gatewayApiId' | 'inputSchemaJson' | 'outputSchemaJson'>;
export type Params = Record<string, string>;
/** What a script passes to a tool, and what a direct call sends. */
export interface ToolRequest {
  path?: Record<string, unknown>;
  query?: Record<string, unknown>;
  headers?: Record<string, unknown>;
  body?: unknown;
}

export const api = {
  me: () => http.get<Me>('/me'),
  adminStatus: () => http.get<{ paused: boolean; activeRuns: number }>('/admin/status'),

  audit: (query?: Query) => http.get<ToolCallRecord[]>('/audit/tool-invocations', query),
  legacy: {
    list: (query?: Query) => http.get<LegacyRecord[]>('/legacy', query),
    get: (table: string, id: string) => http.get<LegacyRecord>(`/legacy/${table}/${id}`),
  },
  catalog: {
    services: () => http.get<ServiceDefinition[]>('/catalog/services'),
    createService: (input: Omit<ServiceDefinition, 'id'>) => http.post<ServiceDefinition>('/catalog/services', input),
    roles: () => http.get<CmdbRole[]>('/catalog/roles'),
    createRole: (input: Omit<CmdbRole, 'id' | 'serviceCode'>) => http.post<CmdbRole>('/catalog/roles', input),
  },
  tools: {
    list: (q?: Query) => http.get<Tool[]>('/tools', q),
    get: (id: string) => http.get<Tool>(`/tools/${id}`),
    versions: (id: string) => http.get<Tool[]>(`/tools/${id}/versions`),
    create: (in_: ToolInput) => http.post<Tool>('/tools', in_),
    update: (id: string, in_: ToolInput) => http.put<Tool>(`/tools/${id}`, in_),
    delete: (id: string) => http.delete(`/tools/${id}`),
  },

  workflows: {
    list: (q?: Query) => http.get<Workflow[]>('/workflows', q),
    get: (id: string) => http.get<Workflow>(`/workflows/${id}`),
    versions: (id: string) => http.get<WorkflowVersion[]>(`/workflows/${id}/versions`),
    version: (id: string, ver: number) => http.get<WorkflowVersion>(`/workflows/${id}/versions/${ver}`),
    create: (name: string, script: string) => http.post<WorkflowVersion>('/workflows', { name, script }),
    saveVersion: (id: string, script: string) => http.post<WorkflowVersion>(`/workflows/${id}/versions`, { script }),
  },

  runs: {
    list: (q?: Query) => http.get<Run[]>('/runs', q),
    get: (id: string) => http.get<Run>(`/runs/${id}`),
    inputs: (id: string) => http.get<ExecutionInput>(`/runs/${id}/inputs`),
    events: (id: string, after: number) => http.get<RunLogEntry[]>(`/runs/${id}/events`, { after }),
    apiCalls: (id: string) => http.get<ApiCallRecord[]>(`/runs/${id}/api-calls`),
    actions: (id: string) => http.get<ExecutionAction[]>(`/runs/${id}/actions`),
    toolCalls: (id: string) => http.get<ToolCallRecord[]>(`/runs/${id}/tool-calls`),
    submit: (workflowId: string, version: number, params: Params) => http.post<Run>('/runs', { workflowId, version, params }),
    adhoc: (script: string, params: unknown) => http.post<Run>('/runs/adhoc', { script, params }),
    call: (toolId: string, toolVersionId: string, request: ToolRequest) => http.post<Run>('/runs/call', { toolId, toolVersionId, request }),
    cancel: (id: string) => http.post<Run>(`/runs/${id}/cancel`),
  },
};
