import { Alert, Button, TextField } from '@mui/material';
import { useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router';
import { api, type ToolRequest } from '../../api';
import { FormPage } from '../../components/layout';
import { TOOL_TYPE_LABEL } from '../../components/StatusChip';
import { useCurrentUser } from '../../hooks/useCurrentUser';
import { useResource } from '../../hooks/useResource';

const mono = { htmlInput: { style: { fontFamily: 'monospace' } } };

function parseObject(label: string, text: string): Record<string, string> | undefined {
  if (!text.trim()) return undefined;
  try {
    return JSON.parse(text) as Record<string, string>;
  } catch {
    throw new Error(`${label} must be a JSON object`);
  }
}

/** Calls one tool directly (/tools/:id/call): no script, the call is made right away and recorded as a run. */
export function ToolCallFormPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const me = useCurrentUser();
  const tool = useResource(`tool:${id}`, () => api.tools.get(id));
  const [path, setPath] = useState<Record<string, string>>({});
  const [query, setQuery] = useState('');
  const [body, setBody] = useState('');

  const t = tool.data;
  const placeholders = t ? [...t.pathTemplate.matchAll(/\{([^}]+)\}/g)].map((m) => m[1]) : [];
  const hasBody = t && !['GET', 'HEAD'].includes(t.method);
  const owned = !!t && !!me?.ownedRoles.includes(t.role);

  const submit = async () => {
    const request: ToolRequest = { path, query: parseObject('Query', query) };
    if (hasBody && body.trim()) {
      try {
        request.body = JSON.parse(body);
      } catch {
        throw new Error('Body must be valid JSON');
      }
    }
    const run = await api.runs.call(id, request);
    navigate(`/runs/${run.id}`);
  };

  return (
    <FormPage
      back={{ to: '/tools', label: 'Tools' }}
      title={t ? `Call ${t.role}.${t.name}` : 'Call tool'}
      subtitle={t && `${TOOL_TYPE_LABEL[t.type]} · ${t.method} ${t.baseUrl ?? ''}${t.pathTemplate}${t.description ? ` · ${t.description}` : ''}`}
      submitLabel="Call"
      onSubmit={submit}
      loading={!t || !me}
      secondaryActions={
        owned && (
          <Button component={RouterLink} to={`/tools/${id}/edit`}>
            Edit tool
          </Button>
        )
      }
    >
      {!owned && t && <Alert severity="warning">None of your teams owns role {t.role}, so the call will be refused.</Alert>}
      <Alert severity="info">The call is made once, right away, and recorded as a run with its request and response.</Alert>
      {placeholders.map((p) => (
        <TextField key={p} required label={p} value={path[p] ?? ''} onChange={(e) => setPath((v) => ({ ...v, [p]: e.target.value }))} slotProps={mono} />
      ))}
      <TextField label="Query (JSON object)" placeholder='{"limit": "10"}' value={query} onChange={(e) => setQuery(e.target.value)} slotProps={mono} />
      {hasBody && <TextField label="Body (JSON)" value={body} onChange={(e) => setBody(e.target.value)} multiline minRows={4} slotProps={mono} />}
    </FormPage>
  );
}
