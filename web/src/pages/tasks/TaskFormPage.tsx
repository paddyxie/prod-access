import { MenuItem, TextField } from '@mui/material';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { api } from '../../api';
import { CodeEditor } from '../../components/CodeEditor';
import { FormPage } from '../../components/layout';
import { useResource } from '../../hooks/useResource';

const TEMPLATE = `export const meta = { title: 'Look up an order', timeout: '5m' };

export const params = {
  id: param.string({ label: 'Order id' }),
};

/** @param {TaskContext} ctx */
export default async function main(ctx) {
  return await ctx.step('lookup', () => api.orders.getOrder({ path: { id: ctx.params.id } }));
}
`;

/** Creates a task (/tasks/new) or saves a new version of one (/tasks/:id/edit). */
export function TaskFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const clients = useResource('clients', () => api.clients.list());
  const apis = useResource('apis', () => api.apis.list());
  const task = useResource(`task-edit:${id}`, async () => {
    if (!id) return { name: '', clientId: '', script: TEMPLATE };
    const t = await api.tasks.get(id);
    const v = await api.tasks.version(id, t.latestVersion);
    return { name: t.name ?? '', clientId: v.clientId, script: v.script };
  });

  const [edits, setEdits] = useState<{ name?: string; clientId?: string; script?: string }>({});
  const form = { ...task.data, ...edits };
  const set = (k: keyof typeof edits) => (v: string) => setEdits((e) => ({ ...e, [k]: v }));

  const submit = async () => {
    const v = id ? await api.tasks.saveVersion(id, form.clientId!, form.script!) : await api.tasks.create(form.name!, form.clientId!, form.script!);
    navigate(`/tasks/${v.taskId}`);
  };

  return (
    <FormPage
      back={id ? { to: `/tasks/${id}`, label: form.name || 'Task' } : { to: '/tasks', label: 'Tasks' }}
      title={id ? `Edit ${form.name ?? ''}` : 'New task'}
      subtitle="Saving inspects the script (meta, params) and derives the API operations it uses; any write operation means its runs need approval."
      submitLabel={id ? 'Save new version' : 'Create task'}
      onSubmit={submit}
      loading={!task.data || !clients.data}
    >
      <TextField required label="Name" value={form.name ?? ''} onChange={(e) => set('name')(e.target.value)} disabled={!!id} />
      <TextField select required label="Client" helperText="The identity every run of this version uses" value={form.clientId ?? ''} onChange={(e) => set('clientId')(e.target.value)}>
        {clients.data?.map((c) => (
          <MenuItem key={c.id} value={c.id} disabled={!c.enabled}>
            {c.name} ({c.environment})
          </MenuItem>
        ))}
      </TextField>
      <CodeEditor value={form.script ?? ''} onChange={set('script')} apis={apis.data} height={520} />
    </FormPage>
  );
}
