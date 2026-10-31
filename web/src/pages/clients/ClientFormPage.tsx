import { FormControlLabel, Switch, TextField } from '@mui/material';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { api, type ClientInput } from '../../api';
import { FormPage } from '../../components/layout';
import { useResource } from '../../hooks/useResource';

const EMPTY: ClientInput = { name: '', environment: 'PROD', credentialRef: '', description: '', enabled: true };

/** Creates a client (/clients/new) or edits one (/clients/:id/edit). */
export function ClientFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const client = useResource(`client:${id}`, () => (id ? api.clients.get(id) : Promise.resolve(EMPTY)));
  const [edits, setEdits] = useState<Partial<ClientInput>>({});
  const form = { ...EMPTY, ...client.data, ...edits };
  const set = <K extends keyof ClientInput>(k: K, v: ClientInput[K]) => setEdits((e) => ({ ...e, [k]: v }));

  const submit = async () => {
    if (id) await api.clients.update(id, edits);
    else await api.clients.create(form);
    navigate('/clients');
  };

  return (
    <FormPage
      back={{ to: '/clients', label: 'Clients' }}
      title={id ? `Edit ${form.name}` : 'New client'}
      submitLabel={id ? 'Save' : 'Create client'}
      onSubmit={submit}
      loading={!client.data}
    >
      <TextField required label="Name" value={form.name} onChange={(e) => set('name', e.target.value)} disabled={!!id} />
      <TextField required label="Environment" value={form.environment} onChange={(e) => set('environment', e.target.value)} />
      <TextField required label="Credential reference" helperText="Key of the client's credentials in the credential store (Vault)" value={form.credentialRef} onChange={(e) => set('credentialRef', e.target.value)} />
      <TextField label="Description" value={form.description ?? ''} onChange={(e) => set('description', e.target.value)} multiline minRows={2} />
      <FormControlLabel label="Enabled — disabled clients cannot be used by new or queued runs" control={<Switch checked={form.enabled} onChange={(e) => set('enabled', e.target.checked)} />} />
    </FormPage>
  );
}
