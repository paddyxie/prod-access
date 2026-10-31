import { Button, MenuItem, TextField } from '@mui/material';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { api, type ApiInput } from '../../api';
import { FormPage } from '../../components/layout';
import { PromptDialog } from '../../components/PromptDialog';
import { useResource } from '../../hooks/useResource';

const METHODS = ['GET', 'HEAD', 'POST', 'PUT', 'PATCH', 'DELETE'];
const EMPTY: ApiInput = { service: '', operation: '', method: 'GET', pathTemplate: '', access: null };

/** Adds an API (/apis/new) or edits one (/apis/:id/edit). */
export function ApiFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const entry = useResource(`api:${id}`, () => (id ? api.apis.get(id) : Promise.resolve(EMPTY)));
  const [edits, setEdits] = useState<Partial<ApiInput>>({});
  const [confirmDelete, setConfirmDelete] = useState(false);
  const form = { ...EMPTY, ...entry.data, ...edits };
  const set = <K extends keyof ApiInput>(k: K, v: ApiInput[K]) => setEdits((e) => ({ ...e, [k]: v }));
  const defaultAccess = form.method === 'GET' || form.method === 'HEAD' ? 'READ' : 'WRITE';

  const submit = async () => {
    await api.apis.save(form);
    navigate('/apis');
  };

  return (
    <FormPage
      back={{ to: '/apis', label: 'API list' }}
      title={id ? `Edit ${form.service}.${form.operation}` : 'New API'}
      submitLabel="Save"
      onSubmit={submit}
      loading={!entry.data}
      secondaryActions={
        id && (
          <Button color="error" onClick={() => setConfirmDelete(true)}>
            Delete
          </Button>
        )
      }
    >
      <TextField required label="Service" value={form.service} onChange={(e) => set('service', e.target.value)} disabled={!!id} />
      <TextField required label="Operation" value={form.operation} onChange={(e) => set('operation', e.target.value)} disabled={!!id} />
      <TextField select required label="Method" value={form.method} onChange={(e) => set('method', e.target.value)}>
        {METHODS.map((m) => (
          <MenuItem key={m} value={m}>
            {m}
          </MenuItem>
        ))}
      </TextField>
      <TextField required label="Path template" helperText="Relative to the service, e.g. /orders/{id}" value={form.pathTemplate} onChange={(e) => set('pathTemplate', e.target.value)} slotProps={{ htmlInput: { style: { fontFamily: 'monospace' } } }} />
      <TextField select label="Access" helperText="Overriding the default is recorded in the audit log" value={form.access ?? ''} onChange={(e) => set('access', e.target.value || null)}>
        <MenuItem value="">Default for {form.method} ({defaultAccess})</MenuItem>
        <MenuItem value="READ">READ</MenuItem>
        <MenuItem value="WRITE">WRITE</MenuItem>
      </TextField>
      <PromptDialog
        open={confirmDelete}
        title="Delete API"
        text="Tasks that use this operation will fail validation on their next save, and queued runs that use it will fail."
        confirmLabel="Delete"
        danger
        onClose={() => setConfirmDelete(false)}
        onConfirm={async () => {
          await api.apis.delete(id!);
          navigate('/apis');
        }}
      />
    </FormPage>
  );
}
