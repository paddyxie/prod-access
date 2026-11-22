import { TextField } from '@mui/material';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { api } from '../../api';
import { CodeEditor } from '../../components/CodeEditor';
import { FormPage } from '../../components/layout';
import { useResource } from '../../hooks/useResource';

const TEMPLATE = `export const meta = { title: 'Look up an order', timeout: '5m' };

export const output = { type: 'object', properties: { id: { type: 'string' }, state: { type: 'string' } } };

export const params = {
  id: param.string({ label: 'Order id' }),
};

/** @param {WorkflowContext} ctx */
export default async function main(ctx) {
  return await ctx.step('lookup', () => api.orders.getOrder({ path: { id: ctx.params.id } }));
}
`;

/** Creates a workflow (/workflows/new) or saves a new version of one (/workflows/:id/edit). */
export function WorkflowFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const tools = useResource('tools', () => api.tools.list());
  const workflow = useResource(`workflow-edit:${id}`, async () => {
    if (!id) return { name: '', script: TEMPLATE };
    const t = await api.workflows.get(id);
    const v = await api.workflows.version(id, t.latestVersion);
    return { name: t.name ?? '', script: v.script };
  });

  const [edits, setEdits] = useState<{ name?: string; script?: string }>({});
  const form = { ...workflow.data, ...edits };
  const set = (k: keyof typeof edits) => (v: string) => setEdits((e) => ({ ...e, [k]: v }));

  const submit = async () => {
    const v = id ? await api.workflows.saveVersion(id, form.script!) : await api.workflows.create(form.name!, form.script!);
    navigate(`/workflows/${v.workflowId}`);
  };

  return (
    <FormPage
      back={id ? { to: `/workflows/${id}`, label: form.name || 'Workflow' } : { to: '/workflows', label: 'Workflows' }}
      title={id ? `Edit ${form.name ?? ''}` : 'New workflow'}
      subtitle="Saving pins the script, parameter definitions, output schema and current tool contract versions. Use ctx.step(name, action, input) to audit workflow actions."
      submitLabel={id ? 'Save new version' : 'Create workflow'}
      onSubmit={submit}
      loading={!workflow.data || !tools.data}
    >
      <TextField required label="Name" value={form.name ?? ''} onChange={(e) => set('name')(e.target.value)} disabled={!!id} />
      <CodeEditor value={form.script ?? ''} onChange={set('script')} tools={tools.data} height={520} />
    </FormPage>
  );
}
