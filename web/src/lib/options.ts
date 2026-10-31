import { api, RUN_STATUSES } from '../api';
import type { Option } from '../components/layout';

// Shared dropdown sources for filters and forms.

export const statusOptions: Option[] = RUN_STATUSES.map((s) => ({ value: s, label: s.replace('_', ' ') }));

export const clientOptions = async (): Promise<Option[]> =>
  (await api.clients.list()).map((c) => ({ value: c.id, label: `${c.name} (${c.environment})` }));

export const taskOptions = async (): Promise<Option[]> => (await api.tasks.list()).map((t) => ({ value: t.id, label: t.name ?? t.id }));
