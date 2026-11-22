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
const EMPTY: ToolInput = { role: '', name: '', type: 'API_FORGE', method: 'GET', pathTemplate: '', baseUrl: null, description: null, gatewayApiId: null, inputSchemaJson: JSON.stringify({ type: "object", properties: { path: {type: "object"}, query: {type: "object"}, headers: {type: "object"}, body: {} }, additionalProperties: false }, null, 2), outputSchemaJson: JSON.stringify({ type: "object" }, null, 2) };
const mono = { htmlInput: { style: { fontFamily: 'monospace' } } };

/** Registers a tool (/tools/new) or edits one (/tools/:id/edit). Role and name are fixed once created. */
export function ToolFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const me = useCurrentUser();
  const roles = useResource('catalog-roles', () => api.catalog.roles());
  const tool = useResource(`tool:${id}`, () => (id ? api.tools.get(id) : Promise.resolve(EMPTY)));
  const [edits, setEdits] = useState<Partial<ToolInput>>({});
  const [confirmDelete, setConfirmDelete] = useState(false);
  const form = { ...EMPTY, ...tool.data, ...edits };
  const set = <K extends keyof ToolInput>(k: K, v: ToolInput[K]) => setEdits((e) => ({ ...e, [k]: v }));
  const http = form.type === 'HTTP_CLIENT';

  const submit = async () => {
    const in_ = { ...form, baseUrl: http ? form.baseUrl : null, gatewayApiId: http ? null : form.gatewayApiId };
    const saved = id ? await api.tools.update(id, in_) : await api.tools.create(in_);
    navigate(`/tools/${saved.id}`);
  };

  return (
    <FormPage
      back={id ? { to: `/tools/${id}`, label: `${form.role}.${form.name}` } : { to: '/tools', label: 'Tools' }}
      title={id ? `Edit ${form.role}.${form.name}` : 'New tool'}
      subtitle="Scripts call it as api.<role>.<name>(). Select a registered system. Saving creates an immutable API contract version."
      submitLabel="Save"
      onSubmit={submit}
      loading={!tool.data || !me || !roles.data}
      loadError={tool.error ?? roles.error}
      secondaryActions={
        id && (
          <Button color="error" onClick={() => setConfirmDelete(true)}>
            Retire
          </Button>
        )
      }
    >
      <TextField select required label="Role" helperText="A CMDB role your team owns" value={form.role} onChange={(e) => set('role', e.target.value)} disabled={!!id}>
        {(id ? [form.role] : (roles.data?.filter((r) => me?.ownedRoles.includes(r.code)).map((r) => r.code) ?? [])).map((r) => (
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
      {!http && <TextField label="API Gateway definition ID" value={form.gatewayApiId ?? ''} onChange={(e) => set('gatewayApiId', e.target.value || null)} helperText="External API definition identifier, when supplied by the gateway" />}
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
      <TextField required label="Input schema (JSON Schema)" value={form.inputSchemaJson} onChange={(e) => set('inputSchemaJson', e.target.value)} multiline minRows={8} slotProps={mono} helperText="Describe the request envelope: path, query, headers, body. Used for validation and editor completion." />
      <TextField required label="Output schema (JSON Schema)" value={form.outputSchemaJson} onChange={(e) => set('outputSchemaJson', e.target.value)} multiline minRows={6} slotProps={mono} helperText="Describe the response body returned to scripts." />
      <PromptDialog
        open={confirmDelete}
        title="Retire tool"
        text="Workflows that call this tool will fail validation on their next save, and queued runs that call it will fail."
        confirmLabel="Retire"
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
