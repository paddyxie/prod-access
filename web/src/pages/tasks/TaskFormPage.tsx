import { TextField } from '@mui/material';
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
  const tools = useResource('tools', () => api.tools.list());
  const task = useResource(`task-edit:${id}`, async () => {
    if (!id) return { name: '', script: TEMPLATE };
    const t = await api.tasks.get(id);
    const v = await api.tasks.version(id, t.latestVersion);
    return { name: t.name ?? '', script: v.script };
  });

  const [edits, setEdits] = useState<{ name?: string; script?: string }>({});
  const form = { ...task.data, ...edits };
  const set = (k: keyof typeof edits) => (v: string) => setEdits((e) => ({ ...e, [k]: v }));

  const submit = async () => {
    const v = id ? await api.tasks.saveVersion(id, form.script!) : await api.tasks.create(form.name!, form.script!);
    navigate(`/tasks/${v.taskId}`);
  };

  return (
    <FormPage
      back={id ? { to: `/tasks/${id}`, label: form.name || 'Task' } : { to: '/tasks', label: 'Tasks' }}
      title={id ? `Edit ${form.name ?? ''}` : 'New task'}
      subtitle="Saving inspects the script (meta, params) and derives the tools it calls; each must be in the tool registry."
      submitLabel={id ? 'Save new version' : 'Create task'}
      onSubmit={submit}
      loading={!task.data || !tools.data}
    >
      <TextField required label="Name" value={form.name ?? ''} onChange={(e) => set('name')(e.target.value)} disabled={!!id} />
      <CodeEditor value={form.script ?? ''} onChange={set('script')} tools={tools.data} height={520} />
    </FormPage>
  );
}
