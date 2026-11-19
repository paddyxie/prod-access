import EditIcon from '@mui/icons-material/Edit';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import { Button, Link } from '@mui/material';
import { Link as RouterLink, useParams } from 'react-router';
import { api, type TaskVersion } from '../../api';
import { CodeEditor } from '../../components/CodeEditor';
import { DataTable, DetailPage, Fields, Section } from '../../components/layout';
import { runColumns } from '../../components/run/runColumns';
import { RoleChip } from '../../components/StatusChip';
import { useCurrentUser } from '../../hooks/useCurrentUser';
import { useResource } from '../../hooks/useResource';
import { formatTime } from '../../lib/format';

export function TaskDetailPage() {
  const { id = '' } = useParams();
  const me = useCurrentUser();
  const task = useResource(`task:${id}`, () => api.tasks.get(id));
  const versions = useResource(`task-versions:${id}`, () => api.tasks.versions(id));
  const runs = useResource(`task-runs:${id}`, () => api.runs.list({ taskId: id, limit: 10 }), 5000);
  const t = task.data;
  const latest = versions.data?.[0];
  const meta = latest ? (JSON.parse(latest.metaJson) as Record<string, unknown> | null) : null;

  return (
    <DetailPage
      back={{ to: '/tasks', label: 'Tasks' }}
      title={t?.name ?? 'Task'}
      subtitle={typeof meta?.title === 'string' ? meta.title : undefined}
      status={t?.roles.map((r) => <RoleChip key={r} role={r} owned={!!me?.ownedRoles.includes(r)} />)}
      loading={!t && !task.error}
      error={task.error}
      actions={
        <>
          <Button variant="outlined" startIcon={<EditIcon />} component={RouterLink} to={`/tasks/${id}/edit`}>
            Edit
          </Button>
          <Button variant="contained" startIcon={<PlayArrowIcon />} component={RouterLink} to={`/tasks/${id}/run`}>
            Run
          </Button>
        </>
      }
    >
      {t && (
        <>
          <Section title="Summary">
            <Fields
              items={[
                ['Latest version', `v${t.latestVersion}`],
                ['Tools', t.tools.join(', ')],
                ['Limits', meta && ['timeout', 'rps', 'maxApiCalls'].filter((k) => meta[k] !== undefined).map((k) => `${k} ${meta[k]}`).join(' · ')],
                ['Created by', t.createdBy],
                ['Created', formatTime(t.createdAt)],
                ['Updated by', t.updatedBy],
                ['Updated', formatTime(t.updatedAt)],
              ]}
            />
          </Section>
          <Section
            title="Recent runs"
            actions={
              <Link component={RouterLink} to={`/runs?taskId=${id}`} variant="body2">
                All runs
              </Link>
            }
          >
            <DataTable columns={runColumns} rows={runs.data} rowKey={(r) => r.id} rowLink={(r) => `/runs/${r.id}`} emptyText="Not run yet." />
          </Section>
          <Section title={`Script (v${t.latestVersion})`}>{latest && <CodeEditor value={latest.script} readOnly height={360} />}</Section>
          <Section title="Versions">
            <DataTable<TaskVersion>
              columns={[
                { header: 'Version', render: (v) => `v${v.ver}`, width: 90 },
                { header: 'Tools', render: (v) => (JSON.parse(v.toolsJson) as string[]).join(', '), mono: true },
                { header: 'Saved by', render: (v) => v.createdBy },
                { header: 'Saved', render: (v) => formatTime(v.createdAt) },
              ]}
              rows={versions.data}
              rowKey={(v) => v.id}
            />
          </Section>
        </>
      )}
    </DetailPage>
  );
}
