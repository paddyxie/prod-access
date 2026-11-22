import EditIcon from '@mui/icons-material/Edit';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import { Alert, Button, Chip, Link, Stack, Typography } from '@mui/material';
import { Link as RouterLink, useParams, useSearchParams } from 'react-router';
import { api, type WorkflowVersion } from '../../api';
import { CodeEditor } from '../../components/CodeEditor';
import { DataTable, DetailPage, Fields, Section } from '../../components/layout';
import { runColumns } from '../../components/run/runColumns';
import { RoleChip } from '../../components/StatusChip';
import { useCurrentUser } from '../../hooks/useCurrentUser';
import { useResource } from '../../hooks/useResource';
import { CodeBlock } from '../../components/layout/CodeBlock';
import { formatTime, prettyJson } from '../../lib/format';

export function WorkflowDetailPage() {
  const { id = '' } = useParams();
  const [query] = useSearchParams();
  const versionNumber = query.get('version');
  const me = useCurrentUser();
  const workflow = useResource(`workflow:${id}`, () => api.workflows.get(id));
  const versions = useResource(`workflow-versions:${id}`, () => api.workflows.versions(id));
  const runs = useResource(`workflow-runs:${id}`, () => api.runs.list({ workflowId: id, limit: 10 }), 5000);
  const t = workflow.data;
  const latest = versions.data?.[0];
  const selected = versionNumber ? versions.data?.find((version) => String(version.ver) === versionNumber) : latest;
  const bindings = selected?.toolBindings ?? [];
  const roles = [...new Set(bindings.map((tool) => tool.key.split('.')[0]))];
  const meta = selected ? (JSON.parse(selected.metaJson) as Record<string, unknown> | null) : null;

  return (
    <DetailPage
      back={{ to: '/workflows', label: 'Workflows' }}
      title={t?.name ?? 'Workflow'}
      subtitle={typeof meta?.title === 'string' ? meta.title : undefined}
      status={selected && <Chip label={`v${selected.ver}`} size="small" variant="outlined" />}
      loading={!t && !workflow.error}
      error={workflow.error ?? versions.error}
      actions={selected && (
        <>
          <Button variant="outlined" startIcon={<EditIcon />} component={RouterLink} to={`/workflows/${id}/edit`}>
            Edit
          </Button>
          <Button variant="contained" startIcon={<PlayArrowIcon />} component={RouterLink} to={`/workflows/${id}/run?version=${selected.ver}`}>
            Run
          </Button>
        </>
      )}
    >
      {t && versions.data && !selected && <Alert severity="error">This version does not belong to the selected workflow.</Alert>}
      {t && selected && (
        <>
          <Section title="Summary">
            <Fields
              items={[
                ['Latest version', `v${t.latestVersion}`],
                ['Systems', <Stack direction="row" sx={{ gap: 0.5, flexWrap: 'wrap' }}>{roles.map((role) => <RoleChip key={role} role={role} owned={!!me?.ownedRoles.includes(role)} />)}</Stack>],
                ['Limits', meta && ['timeout', 'rps', 'maxApiCalls'].filter((k) => meta[k] !== undefined).map((k) => `${k} ${meta[k]}`).join(' · ')],
                ['Created by', t.createdBy],
                ['Created', formatTime(t.createdAt)],
                ['Updated by', t.updatedBy],
                ['Updated', formatTime(t.updatedAt)],
              ]}
            />
          </Section>
          <Section title="Tool dependencies">
            <DataTable rows={bindings} rowKey={(tool) => tool.versionId} rowLink={(tool) => `/tools/${tool.toolId}?version=${tool.versionId}`} emptyText="This version uses no tools." columns={[
              { header: 'Tool', render: (tool) => tool.key, mono: true },
              { header: 'Pinned version', render: (tool) => `v${tool.ver}` },
              { header: 'Availability', render: (tool) => tool.retired ? 'Retired' : 'Active' },
            ]} />
          </Section>
          <Section
            title="Recent runs"
            actions={
              <Link component={RouterLink} to={`/runs?workflowId=${id}`} variant="body2">
                All runs
              </Link>
            }
          >
            <DataTable columns={runColumns} rows={runs.data} rowKey={(r) => r.id} rowLink={(r) => `/runs/${r.id}`} emptyText="Not run yet." />
          </Section>
          <Section title={`Script (v${selected.ver})`}><CodeEditor value={selected.script} readOnly height={360} /></Section>
          <Section title="Output contract">
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>JSON Schema for the workflow return value</Typography>
            <CodeBlock text={prettyJson(selected.outputSchemaJson)} />
          </Section>
          <Section title="Versions">
            <DataTable<WorkflowVersion>
              columns={[
                { header: 'Version', render: (v) => `v${v.ver}`, width: 90 },
                { header: 'Tools', render: (v) => v.toolBindings.map((tool) => `${tool.key} v${tool.ver}`).join(', '), mono: true },
                { header: 'Saved by', render: (v) => v.createdBy },
                { header: 'Saved', render: (v) => formatTime(v.createdAt) },
              ]}
              rows={versions.data}
              rowKey={(v) => v.id}
              rowLink={(v) => `/workflows/${id}?version=${v.ver}`}
            />
          </Section>
        </>
      )}
    </DetailPage>
  );
}
