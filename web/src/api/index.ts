import { http, type Query } from './http';
import type { ApiCallRecord, ApiEntry, Client, Run, RunLogEntry, Task, TaskVersion } from './types';

export { ApiError } from './http';
export * from './types';

export type ClientInput = Pick<Client, 'name' | 'environment' | 'credentialRef' | 'description' | 'enabled'>;
export type ApiInput = Pick<ApiEntry, 'service' | 'operation' | 'method' | 'pathTemplate'> & { access: string | null };
export type Params = Record<string, string>;

export const api = {
  me: () => http.get<{ user: string }>('/me'),
  adminStatus: () => http.get<{ paused: boolean; activeRuns: number }>('/admin/status'),

  clients: {
    list: (q?: Query) => http.get<Client[]>('/clients', q),
    get: (id: string) => http.get<Client>(`/clients/${id}`),
    create: (in_: ClientInput) => http.post<Client>('/clients', in_),
    update: (id: string, in_: Partial<ClientInput>) => http.put<Client>(`/clients/${id}`, in_),
  },

  apis: {
    list: (q?: Query) => http.get<ApiEntry[]>('/apis', q),
    get: (id: string) => http.get<ApiEntry>(`/apis/${id}`),
    save: (in_: ApiInput) => http.post<ApiEntry>('/apis', in_),
    delete: (id: string) => http.delete(`/apis/${id}`),
  },

  tasks: {
    list: (q?: Query) => http.get<Task[]>('/tasks', q),
    get: (id: string) => http.get<Task>(`/tasks/${id}`),
    versions: (id: string) => http.get<TaskVersion[]>(`/tasks/${id}/versions`),
    version: (id: string, ver: number) => http.get<TaskVersion>(`/tasks/${id}/versions/${ver}`),
    create: (name: string, clientId: string, script: string) => http.post<TaskVersion>('/tasks', { name, clientId, script }),
    saveVersion: (id: string, clientId: string, script: string) => http.post<TaskVersion>(`/tasks/${id}/versions`, { clientId, script }),
  },

  runs: {
    list: (q?: Query) => http.get<Run[]>('/runs', q),
    get: (id: string) => http.get<Run>(`/runs/${id}`),
    events: (id: string, after: number) => http.get<RunLogEntry[]>(`/runs/${id}/events`, { after }),
    apiCalls: (id: string) => http.get<ApiCallRecord[]>(`/runs/${id}/api-calls`),
    submit: (taskId: string, params: Params, confirmClient: string) => http.post<Run>('/runs', { taskId, params, confirmClient }),
    adhoc: (clientId: string, script: string, params: unknown, confirmClient: string) =>
      http.post<Run>('/runs/adhoc', { clientId, script, params, confirmClient }),
    approve: (id: string) => http.post<Run>(`/runs/${id}/approve`),
    reject: (id: string, reason: string) => http.post<Run>(`/runs/${id}/reject`, { reason }),
    cancel: (id: string) => http.post<Run>(`/runs/${id}/cancel`),
  },
};
