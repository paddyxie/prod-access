import { MenuItem, TextField } from '@mui/material';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { api } from '../../api';
import { CodeEditor } from '../../components/CodeEditor';
import { FormPage } from '../../components/layout';
import { useResource } from '../../hooks/useResource';

const TEMPLATE = `/** @param {TaskContext} ctx */
export default async function main(ctx) {
  return 'hello';
}
`;

export function AdHocRunFormPage() {
  const navigate = useNavigate();
  const clients = useResource('clients', () => api.clients.list());
  const apis = useResource('apis', () => api.apis.list());
  const [clientId, setClientId] = useState('');
  const [script, setScript] = useState(TEMPLATE);
  const [params, setParams] = useState('{}');
  const [confirm, setConfirm] = useState('');

  const submit = async () => {
    let parsed: unknown;
    try {
      parsed = JSON.parse(params);
    } catch {
      throw new Error('Parameters must be valid JSON');
    }
    const run = await api.runs.adhoc(clientId, script, parsed, confirm);
    navigate(`/runs/${run.id}`);
  };

  return (
    <FormPage
      back={{ to: '/runs', label: 'Runs' }}
      title="Run an ad-hoc script"
      subtitle="Runs once, as the chosen client. Scripts that write data need someone else's approval before they run."
      submitLabel="Submit"
      onSubmit={submit}
      loading={!clients.data}
    >
      <TextField select required label="Client" value={clientId} onChange={(e) => setClientId(e.target.value)}>
        {clients.data?.map((c) => (
          <MenuItem key={c.id} value={c.id} disabled={!c.enabled}>
            {c.name} ({c.environment})
          </MenuItem>
        ))}
      </TextField>
      <CodeEditor value={script} onChange={setScript} apis={apis.data} />
      <TextField label="Parameters (JSON)" value={params} onChange={(e) => setParams(e.target.value)} multiline minRows={2} slotProps={{ htmlInput: { style: { fontFamily: 'monospace' } } }} />
      <TextField label="Confirm client name" helperText="Required when the script writes data: type the client name." value={confirm} onChange={(e) => setConfirm(e.target.value)} />
    </FormPage>
  );
}
