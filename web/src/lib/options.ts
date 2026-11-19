import { api, RUN_STATUSES, TOOL_TYPES } from '../api';
import type { Option } from '../components/layout';
import { TOOL_TYPE_LABEL } from '../components/StatusChip';

// Shared dropdown sources for filters and forms.

export const statusOptions: Option[] = RUN_STATUSES.map((s) => ({ value: s, label: s.replace('_', ' ') }));

export const kindOptions: Option[] = [
  { value: 'SCRIPT', label: 'Script' },
  { value: 'CALL', label: 'Single call' },
];

export const toolTypeOptions: Option[] = TOOL_TYPES.map((t) => ({ value: t, label: TOOL_TYPE_LABEL[t] }));

export const roleOptions = async (): Promise<Option[]> =>
  [...new Set((await api.tools.list()).map((t) => t.role))].map((r) => ({ value: r, label: r }));

export const taskOptions = async (): Promise<Option[]> => (await api.tasks.list()).map((t) => ({ value: t.id, label: t.name ?? t.id }));
