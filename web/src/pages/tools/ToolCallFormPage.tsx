import { Alert, TextField } from '@mui/material';
import { useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { api, type ToolRequest } from '../../api';
import { FormPage } from '../../components/layout';
import { TOOL_TYPE_LABEL } from '../../components/StatusChip';
import { useCurrentUser } from '../../hooks/useCurrentUser';
import { useResource } from '../../hooks/useResource';
import { prettyJson } from '../../lib/format';
import { monoFont } from '../../theme';

const mono = { htmlInput: { style: { fontFamily: monoFont } } };

function parseJson(label: string, text: string): unknown {
  try {
    return JSON.parse(text) as unknown;
  } catch {
    throw new Error(`${label} must be valid JSON`);
  }
}

function parseObject(label: string, text: string): Record<string, unknown> | undefined {
  if (!text.trim()) return undefined;
  const value = parseJson(label, text);
  if (value === null || typeof value !== 'object' || Array.isArray(value)) throw new Error(`${label} must be a JSON object`);
  return value as Record<string, unknown>;
}

export function ToolCallFormPage() {
  const { id = '' } = useParams();
  const [search] = useSearchParams();
  const versionId = search.get('version');
  const navigate = useNavigate();
  const me = useCurrentUser();
  const tool = useResource(`tool-call:${id}:${versionId}`, async () => {
    if (!versionId) return api.tools.get(id);
    const version = (await api.tools.versions(id)).find((item) => item.versionId === versionId);
    if (!version) throw new Error('This version does not belong to the selected tool.');
    return version;
  });
  const [path, setPath] = useState<Record<string, string>>({});
  const [query, setQuery] = useState('');
  const [headers, setHeaders] = useState('');
  const [body, setBody] = useState('');

  const t = tool.data;
  const placeholders = t ? [...new Set([...t.pathTemplate.matchAll(/\{([^}]+)\}/g)].map((match) => match[1]))] : [];
  const owned = !!t && !!me?.ownedRoles.includes(t.role);
  const submit = async () => {
    if (!t) return;
    const request: ToolRequest = { path: placeholders.length ? path : undefined, query: parseObject('Query', query), headers: parseObject('Headers', headers) };
    if (body.trim()) request.body = parseJson('Body', body);
    const run = await api.runs.call(id, t.versionId, request);
    navigate(`/runs/${run.id}`);
  };

  return (
    <FormPage
      back={{ to: t ? `/tools/${id}?version=${t.versionId}` : '/tools', label: t ? `${t.role}.${t.name}` : 'Tools' }}
      title={t ? `Call ${t.role}.${t.name}` : 'Call tool'}
      subtitle={t && `${t.service} · v${t.ver} · ${TOOL_TYPE_LABEL[t.type]} · ${t.method} ${t.baseUrl ?? ''}${t.pathTemplate}`}
      submitLabel="Call" onSubmit={submit} loading={!t || !me} loadError={tool.error} submitDisabled={!owned || t?.retired}
    >
      {t?.retired && <Alert severity="warning">This tool has been retired.</Alert>}
      {!owned && t && <Alert severity="warning">Your team needs ownership of {t.role} to call this tool.</Alert>}
      {t && <TextField label="Input contract" value={prettyJson(t.inputSchemaJson)} multiline minRows={3} maxRows={12} slotProps={{ ...mono, input: { readOnly: true } }} />}
      {placeholders.map((parameter) => (
        <TextField key={parameter} required label={parameter} value={path[parameter] ?? ''} onChange={(e) => setPath((values) => ({ ...values, [parameter]: e.target.value }))} slotProps={mono} />
      ))}
      <TextField label="Query (JSON object)" placeholder='{"limit": 10}' value={query} onChange={(e) => setQuery(e.target.value)} slotProps={mono} />
      <TextField label="Headers (JSON object)" placeholder='{"X-Request-Reason": "data repair"}' value={headers} onChange={(e) => setHeaders(e.target.value)} slotProps={mono} />
      <TextField label="Body (JSON)" value={body} onChange={(e) => setBody(e.target.value)} multiline minRows={4} slotProps={mono} />
    </FormPage>
  );
}
