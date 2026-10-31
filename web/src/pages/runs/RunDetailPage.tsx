import { Alert, Box, Button, Link, Tooltip, Typography } from '@mui/material';
import { useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router';
import { ACTIVE_STATUSES, api, type ApiCallRecord, type Run } from '../../api';
import { CodeEditor } from '../../components/CodeEditor';
import { DataTable, DetailPage, Fields, Section, type Column } from '../../components/layout';
import { PromptDialog } from '../../components/PromptDialog';
import { RunLog } from '../../components/run/RunLog';
import { StatusChip } from '../../components/StatusChip';
import { useCurrentUser } from '../../hooks/useCurrentUser';
import { useResource } from '../../hooks/useResource';
import { formatDuration, formatTime, prettyJson, shortId } from '../../lib/format';

const callColumns: Column<ApiCallRecord>[] = [
  { header: 'Time', render: (c) => new Date(c.ts).toLocaleTimeString(), width: 110 },
  { header: 'Operation', render: (c) => `${c.service}.${c.operation}`, mono: true },
  { header: 'Request', render: (c) => `${c.method} ${c.path}`, mono: true },
  { header: 'Status', render: (c) => c.status ?? '—', width: 80 },
  { header: 'Duration', render: (c) => (c.durationMs == null ? '—' : `${c.durationMs} ms`), width: 100 },
  { header: 'Error', render: (c) => c.error },
];

export function RunDetailPage() {
  const { id = '' } = useParams();
  const user = useCurrentUser();
  const [live, setLive] = useState(true);
  const run = useResource(`run:${id}`, () => api.runs.get(id), live ? 2000 : undefined);
  const r = run.data;
  if (r && live !== ACTIVE_STATUSES.includes(r.status)) setLive(!live);
  const version = useResource(`run-version:${r?.taskId}:${r?.ver}`, () => (r ? api.tasks.version(r.taskId, r.ver) : Promise.resolve(null)));
  const calls = useResource(`run-calls:${id}:${r?.status}`, () => api.runs.apiCalls(id), live ? 3000 : undefined);

  const [actionError, setActionError] = useState<Error | null>(null);
  const [dialog, setDialog] = useState<'reject' | 'cancel' | null>(null);
  const act = async (action: () => Promise<Run>) => {
    setDialog(null);
    setActionError(null);
    try {
      await action();
      run.reload();
    } catch (e) {
      setActionError(e as Error);
    }
  };

  const ownRun = r?.submittedBy === user;
  return (
    <DetailPage
      back={{ to: '/runs', label: 'Runs' }}
      title={`Run ${shortId(id)}`}
      subtitle={r && <>{r.taskName ? <Link component={RouterLink} to={`/tasks/${r.taskId}`}>{r.taskName}</Link> : 'Ad-hoc script'} · v{r.ver} · as client {r.clientName}</>}
      status={r && <StatusChip status={r.status} />}
      loading={!r && !run.error}
      error={run.error ?? actionError}
      actions={
        r && (
          <>
            {r.status === 'PENDING_APPROVAL' && (
              <>
                <Tooltip title={ownRun ? 'A run must be approved by someone other than its submitter' : ''}>
                  <span>
                    <Button variant="contained" disabled={ownRun} onClick={() => act(() => api.runs.approve(id))}>
                      Approve
                    </Button>
                  </span>
                </Tooltip>
                <Button color="error" variant="outlined" onClick={() => setDialog('reject')}>
                  Reject
                </Button>
              </>
            )}
            {ACTIVE_STATUSES.includes(r.status) && !r.cancelRequested && (
              <Button variant="outlined" onClick={() => setDialog('cancel')}>
                Cancel run
              </Button>
            )}
          </>
        )
      }
    >
      {r && (
        <>
          {r.status === 'PENDING_APPROVAL' && (
            <Alert severity="warning">
              This run writes data as client <b>{r.clientName}</b>. Approve only if the script and parameters below are what should run.
            </Alert>
          )}
          <Section title="Summary">
            <Fields
              items={[
                ['Submitted by', r.submittedBy],
                ['Approval', r.requiresApproval ? 'Required (writes data)' : 'Not required (read-only)'],
                ['Approved by', r.approvedBy],
                ['Operations', version.data && (JSON.parse(version.data.opsJson) as string[]).join(', ')],
                ['Created', formatTime(r.createdAt)],
                ['Started', formatTime(r.startedAt)],
                ['Finished', formatTime(r.finishedAt)],
                ['Duration', formatDuration(r.startedAt, r.finishedAt)],
              ]}
            />
          </Section>
          <Section title="Parameters">
            <Mono text={prettyJson(r.paramsJson)} />
          </Section>
          {(r.resultJson || r.error) && (
            <Section title={r.error ? 'Error' : 'Result'}>
              {r.error ? <Alert severity="error" sx={{ whiteSpace: 'pre-wrap' }}>{r.error}</Alert> : <Mono text={prettyJson(r.resultJson)} />}
            </Section>
          )}
          <Section title="Log">
            <RunLog key={id} runId={id} live={live} />
          </Section>
          <Section title={`API calls (${calls.data?.length ?? 0})`}>
            <DataTable columns={callColumns} rows={calls.data} rowKey={(c) => c.id} emptyText="No API calls." />
          </Section>
          <Section title="Script">{version.data && <CodeEditor value={version.data.script} readOnly height={360} />}</Section>
        </>
      )}
      <PromptDialog
        open={dialog === 'reject'}
        title="Reject run"
        text="The run will not execute. The reason is recorded in the audit log."
        inputLabel="Reason"
        confirmLabel="Reject"
        danger
        onClose={() => setDialog(null)}
        onConfirm={(reason) => act(() => api.runs.reject(id, reason))}
      />
      <PromptDialog
        open={dialog === 'cancel'}
        title="Cancel run"
        text="A queued run is dropped; a running one is stopped and its process killed. API calls already made are not undone."
        confirmLabel="Cancel run"
        danger
        onClose={() => setDialog(null)}
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
