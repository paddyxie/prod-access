import { Alert, Box, Button, Link, Typography } from '@mui/material';
import { useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router';
import { ACTIVE_STATUSES, api, type Run, type ToolCallRecord } from '../../api';
import { CodeEditor } from '../../components/CodeEditor';
import { DataTable, DetailPage, Fields, Section, type Column } from '../../components/layout';
import { PromptDialog } from '../../components/PromptDialog';
import { RunLog } from '../../components/run/RunLog';
import { runSubject } from '../../components/run/runColumns';
import { StatusChip, ToolTypeChip } from '../../components/StatusChip';
import { useResource } from '../../hooks/useResource';
import { formatDuration, formatTime, prettyJson, shortId } from '../../lib/format';

const callColumns: Column<ToolCallRecord>[] = [
  { header: 'Time', render: (c) => new Date(c.ts).toLocaleTimeString(), width: 110 },
  { header: 'Tool', render: (c) => `${c.role}.${c.tool}`, mono: true },
  { header: 'Type', render: (c) => <ToolTypeChip type={c.type} />, width: 130 },
  { header: 'Request', render: (c) => `${c.method} ${c.path}`, mono: true },
  { header: 'Status', render: (c) => c.status ?? '—', width: 80 },
  { header: 'Duration', render: (c) => (c.durationMs == null ? '—' : `${c.durationMs} ms`), width: 100 },
  { header: 'Error', render: (c) => c.error },
];

export function RunDetailPage() {
  const { id = '' } = useParams();
  const [live, setLive] = useState(true);
  const run = useResource(`run:${id}`, () => api.runs.get(id), live ? 2000 : undefined);
  const r = run.data;
  if (r && live !== ACTIVE_STATUSES.includes(r.status)) setLive(!live);
  const script = r?.kind === 'SCRIPT';
  const version = useResource(`run-version:${r?.taskId}:${r?.ver}`, () =>
    r?.taskId && r.ver != null ? api.tasks.version(r.taskId, r.ver) : Promise.resolve(null),
  );
  const calls = useResource(`run-calls:${id}:${r?.status}`, () => api.runs.toolCalls(id), live ? 3000 : undefined);

  const [actionError, setActionError] = useState<Error | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const act = async (action: () => Promise<Run>) => {
    setCancelling(false);
    setActionError(null);
    try {
      await action();
      run.reload();
    } catch (e) {
      setActionError(e as Error);
    }
  };

  return (
    <DetailPage
      back={{ to: '/runs', label: 'Runs' }}
      title={`Run ${shortId(id)}`}
      subtitle={
        r && (
          <>
            {r.taskId && r.taskName ? <Link component={RouterLink} to={`/tasks/${r.taskId}`}>{r.taskName}</Link> : runSubject(r)}
            {script && ` · v${r.ver}`} · {script ? 'script' : 'single call'}
          </>
        )
      }
      status={r && <StatusChip status={r.status} />}
      loading={!r && !run.error}
      error={run.error ?? actionError}
      actions={
        r && script && ACTIVE_STATUSES.includes(r.status) && !r.cancelRequested && (
          <Button variant="outlined" onClick={() => setCancelling(true)}>
            Cancel run
          </Button>
        )
      }
    >
      {r && (
        <>
          <Section title="Summary">
            <Fields
              items={[
                ['Submitted by', r.submittedBy],
                ['Roles checked', (JSON.parse(r.rolesJson) as string[]).join(', ')],
                ['Tools', script ? version.data && (JSON.parse(version.data.toolsJson) as string[]).join(', ') : r.toolKey],
                ['Created', formatTime(r.createdAt)],
                ['Started', formatTime(r.startedAt)],
                ['Finished', formatTime(r.finishedAt)],
                ['Duration', formatDuration(r.startedAt, r.finishedAt)],
              ]}
            />
          </Section>
          <Section title={script ? 'Parameters' : 'Request'}>
            <Mono text={prettyJson(r.paramsJson)} />
          </Section>
          {r.error && (
            <Section title="Error">
              <Alert severity="error" sx={{ whiteSpace: 'pre-wrap' }}>{r.error}</Alert>
            </Section>
          )}
          {r.resultJson && (
            <Section title={script ? 'Result' : 'Response'}>
              <Mono text={prettyJson(r.resultJson)} />
            </Section>
          )}
          {script && (
            <Section title="Log">
              <RunLog key={id} runId={id} live={live} />
            </Section>
          )}
          <Section title={`Tool calls (${calls.data?.length ?? 0})`}>
            <DataTable columns={callColumns} rows={calls.data} rowKey={(c) => c.id} emptyText="No tool calls." />
          </Section>
          {script && <Section title="Script">{version.data && <CodeEditor value={version.data.script} readOnly height={360} />}</Section>}
        </>
      )}
      <PromptDialog
        open={cancelling}
        title="Cancel run"
        text="A queued run is dropped; a running one is stopped and its process killed. Tool calls already made are not undone."
        confirmLabel="Cancel run"
        danger
        onClose={() => setCancelling(false)}
        onConfirm={() => act(() => api.runs.cancel(id))}
      />
    </DetailPage>
  );
}

function Mono({ text }: { text: string }) {
  if (!text) return <Typography color="text.secondary">—</Typography>;
  return (
    <Box component="pre" sx={{ m: 0, p: 1.5, bgcolor: 'action.hover', borderRadius: 1, fontSize: 12.5, overflow: 'auto', maxHeight: 360 }}>
      {text}
    </Box>
  );
}
