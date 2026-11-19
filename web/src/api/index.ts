import { http, type Query } from './http';
import type { Me, Run, RunLogEntry, Task, TaskVersion, Tool, ToolCallRecord } from './types';

export { ApiError } from './http';
export * from './types';

export type ToolInput = Pick<Tool, 'role' | 'name' | 'type' | 'method' | 'pathTemplate' | 'baseUrl' | 'description'>;
export type Params = Record<string, string>;
/** What a script passes to a tool, and what a direct call sends. */
export interface ToolRequest {
  path?: Record<string, string>;
  query?: Record<string, string>;
  headers?: Record<string, string>;
  body?: unknown;
}

export const api = {
  me: () => http.get<Me>('/me'),
  adminStatus: () => http.get<{ paused: boolean; activeRuns: number }>('/admin/status'),

  tools: {
    list: (q?: Query) => http.get<Tool[]>('/tools', q),
    get: (id: string) => http.get<Tool>(`/tools/${id}`),
    create: (in_: ToolInput) => http.post<Tool>('/tools', in_),
    update: (id: string, in_: ToolInput) => http.put<Tool>(`/tools/${id}`, in_),
    delete: (id: string) => http.delete(`/tools/${id}`),
  },

  tasks: {
    list: (q?: Query) => http.get<Task[]>('/tasks', q),
    get: (id: string) => http.get<Task>(`/tasks/${id}`),
    versions: (id: string) => http.get<TaskVersion[]>(`/tasks/${id}/versions`),
    version: (id: string, ver: number) => http.get<TaskVersion>(`/tasks/${id}/versions/${ver}`),
    create: (name: string, script: string) => http.post<TaskVersion>('/tasks', { name, script }),
    saveVersion: (id: string, script: string) => http.post<TaskVersion>(`/tasks/${id}/versions`, { script }),
  },

  runs: {
    list: (q?: Query) => http.get<Run[]>('/runs', q),
    get: (id: string) => http.get<Run>(`/runs/${id}`),
    events: (id: string, after: number) => http.get<RunLogEntry[]>(`/runs/${id}/events`, { after }),
    toolCalls: (id: string) => http.get<ToolCallRecord[]>(`/runs/${id}/tool-calls`),
    submit: (taskId: string, params: Params) => http.post<Run>('/runs', { taskId, params }),
    adhoc: (script: string, params: unknown) => http.post<Run>('/runs/adhoc', { script, params }),
    call: (toolId: string, request: ToolRequest) => http.post<Run>('/runs/call', { toolId, request }),
    cancel: (id: string) => http.post<Run>(`/runs/${id}/cancel`),
  },
};
