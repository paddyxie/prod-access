import { Button, MenuItem, TextField } from '@mui/material';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { api, TOOL_TYPES, type ToolInput } from '../../api';
import { FormPage } from '../../components/layout';
import { PromptDialog } from '../../components/PromptDialog';
import { TOOL_TYPE_LABEL } from '../../components/StatusChip';
import { useCurrentUser } from '../../hooks/useCurrentUser';
import { useResource } from '../../hooks/useResource';

const METHODS = ['GET', 'HEAD', 'POST', 'PUT', 'PATCH', 'DELETE'];
const EMPTY: ToolInput = { role: '', name: '', type: 'API_FORGE', method: 'GET', pathTemplate: '', baseUrl: null, description: null };
const mono = { htmlInput: { style: { fontFamily: 'monospace' } } };

/** Registers a tool (/tools/new) or edits one (/tools/:id/edit). Role and name are fixed once created. */
export function ToolFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const me = useCurrentUser();
  const tool = useResource(`tool:${id}`, () => (id ? api.tools.get(id) : Promise.resolve(EMPTY)));
  const [edits, setEdits] = useState<Partial<ToolInput>>({});
  const [confirmDelete, setConfirmDelete] = useState(false);
  const form = { ...EMPTY, ...tool.data, ...edits };
  const set = <K extends keyof ToolInput>(k: K, v: ToolInput[K]) => setEdits((e) => ({ ...e, [k]: v }));
  const http = form.type === 'HTTP_CLIENT';

  const submit = async () => {
    const in_ = { ...form, baseUrl: http ? form.baseUrl : null };
    const saved = id ? await api.tools.update(id, in_) : await api.tools.create(in_);
    navigate(`/tools/${saved.id}/call`);
  };

  return (
    <FormPage
      back={id ? { to: `/tools/${id}/call`, label: `${form.role}.${form.name}` } : { to: '/tools', label: 'Tools' }}
      title={id ? `Edit ${form.role}.${form.name}` : 'New tool'}
      subtitle="Scripts call it as api.<role>.<name>(). Only teams that own the role may add or change its tools."
      submitLabel="Save"
      onSubmit={submit}
      loading={!tool.data || !me}
      secondaryActions={
        id && (
          <Button color="error" onClick={() => setConfirmDelete(true)}>
            Delete
          </Button>
        )
      }
    >
      <TextField select required label="Role" helperText="A CMDB role your team owns" value={form.role} onChange={(e) => set('role', e.target.value)} disabled={!!id}>
        {(id ? [form.role] : (me?.ownedRoles ?? [])).map((r) => (
          <MenuItem key={r} value={r}>
            {r}
          </MenuItem>
        ))}
      </TextField>
      <TextField required label="Name" helperText="A JS identifier, e.g. getOrder" value={form.name} onChange={(e) => set('name', e.target.value)} disabled={!!id} slotProps={mono} />
      <TextField select required label="Type" value={form.type} onChange={(e) => set('type', e.target.value as ToolInput['type'])}>
        {TOOL_TYPES.map((t) => (
          <MenuItem key={t} value={t}>
            {TOOL_TYPE_LABEL[t]}
          </MenuItem>
        ))}
      </TextField>
      {http && (
        <TextField required label="Base URL" helperText="The endpoint is called directly, not through API Forge" value={form.baseUrl ?? ''} onChange={(e) => set('baseUrl', e.target.value)} slotProps={mono} />
      )}
      <TextField select required label="Method" value={form.method} onChange={(e) => set('method', e.target.value)}>
        {METHODS.map((m) => (
          <MenuItem key={m} value={m}>
            {m}
          </MenuItem>
        ))}
      </TextField>
      <TextField
        required
        label="Path template"
        helperText={http ? 'Appended to the base URL, e.g. /orders/{id}' : "Relative to the role's API Forge prefix, e.g. /orders/{id}"}
        value={form.pathTemplate}
        onChange={(e) => set('pathTemplate', e.target.value)}
        slotProps={mono}
      />
      <TextField label="Description" helperText="Shown in the editor's completion" value={form.description ?? ''} onChange={(e) => set('description', e.target.value)} multiline minRows={2} />
      <PromptDialog
        open={confirmDelete}
        title="Delete tool"
        text="Tasks that call this tool will fail validation on their next save, and queued runs that call it will fail."
        confirmLabel="Delete"
        danger
        onClose={() => setConfirmDelete(false)}
        onConfirm={async () => {
          await api.tools.delete(id!);
          navigate('/tools');
        }}
      />
    </FormPage>
  );
}
