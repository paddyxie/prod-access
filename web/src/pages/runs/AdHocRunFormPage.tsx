import { TextField } from '@mui/material';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { api } from '../../api';
import { CodeEditor } from '../../components/CodeEditor';
import { FormPage } from '../../components/layout';
import { useResource } from '../../hooks/useResource';

const TEMPLATE = `/** @param {WorkflowContext} ctx */
export default async function main(ctx) {
  return 'hello';
}
`;

export function AdHocRunFormPage() {
  const navigate = useNavigate();
  const tools = useResource('tools', () => api.tools.list());
  const [script, setScript] = useState(TEMPLATE);
  const [params, setParams] = useState('{}');

  const submit = async () => {
    let parsed: unknown;
    try {
      parsed = JSON.parse(params);
    } catch {
      throw new Error('Parameters must be valid JSON');
    }
    const run = await api.runs.adhoc(script, parsed);
    navigate(`/runs/${run.id}`);
  };

  return (
    <FormPage
      back={{ to: '/runs', label: 'Executions' }}
      title="Run an ad-hoc script"
      subtitle="Runs once in the sandbox. Your teams must own every role whose tools the script calls."
      submitLabel="Run"
      onSubmit={submit}
      loading={!tools.data}
    >
      <CodeEditor value={script} onChange={setScript} tools={tools.data} />
      <TextField label="Parameters (JSON)" value={params} onChange={(e) => setParams(e.target.value)} multiline minRows={2} slotProps={{ htmlInput: { style: { fontFamily: 'monospace' } } }} />
    </FormPage>
  );
}
