import { ExecutionTrace } from '../../components/run/ExecutionTrace';
import { Alert, Button, Link } from '@mui/material';
import { useState } from 'react';
import { Link as RouterLink, useParams, useSearchParams } from 'react-router';
import { ACTIVE_STATUSES, api, type Run } from '../../api';
import { CodeEditor } from '../../components/CodeEditor';
import { DetailPage, Fields, Section } from '../../components/layout';
import { PromptDialog } from '../../components/PromptDialog';
import { RunLog } from '../../components/run/RunLog';
import { runSubject } from '../../components/run/runColumns';
import { StatusChip } from '../../components/StatusChip';
import { useResource } from '../../hooks/useResource';
import { CodeBlock } from '../../components/layout/CodeBlock';
import { formatDuration, formatTime, prettyJson, shortId } from '../../lib/format';

export function RunDetailPage() {
  const { id = '' } = useParams();
  const [query] = useSearchParams();
  const invocationId = query.get('invocation');
  const [live, setLive] = useState(true);
  const run = useResource(`run:${id}`, () => api.runs.get(id), live ? 2000 : undefined);
  const r = run.data;
  if (r && live !== ACTIVE_STATUSES.includes(r.status)) setLive(!live);
  const script = r?.kind === 'SCRIPT';
  const version = useResource(`run-version:${r?.workflowId}:${r?.ver}`, () =>
    r?.workflowId && r.ver != null ? api.workflows.version(r.workflowId, r.ver) : Promise.resolve(null),
  );
  const calls = useResource(`run-calls:${id}:${r?.status}`, () => api.runs.toolCalls(id), live ? 3000 : undefined);

  const inputs = useResource(`run-inputs:${id}`, () => api.runs.inputs(id));
  const requests = useResource(`run-http:${id}:${r?.status}`, () => api.runs.apiCalls(id), live ? 3000 : undefined);
  const actions = useResource(`run-actions:${id}:${r?.status}`, () => api.runs.actions(id), live ? 3000 : undefined);

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
      back={{ to: '/runs', label: 'Executions' }}
      title={`Execution ${shortId(id)}`}
      subtitle={
        r && (
          <>
            {r.workflowId && r.workflowName ? <Link component={RouterLink} to={`/workflows/${r.workflowId}?version=${r.ver}`}>{r.workflowName}</Link> : runSubject(r)}
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
                ['Tools', script ? version.data?.toolBindings.map((tool) => `${tool.key} v${tool.ver}`).join(', ') : r.toolKey],
                ['Created', formatTime(r.createdAt)],
                ['Started', formatTime(r.startedAt)],
                ['Finished', formatTime(r.finishedAt)],
                ['Duration', formatDuration(r.startedAt, r.finishedAt)],
              ]}
            />
          </Section>
          <Section title="Execution audit">
            {(calls.error || requests.error || actions.error) && <Alert severity="error">{(calls.error ?? requests.error ?? actions.error)?.message}</Alert>}
            <ExecutionTrace calls={calls.data ?? []} requests={requests.data ?? []} actions={actions.data ?? []} selectedInvocation={invocationId} />
          </Section>
          {script && inputs.data && <Section title="Submitted input"><CodeBlock text={prettyJson(inputs.data.submittedJson)} /></Section>}
          <Section title={script ? 'Validated input' : 'Request'}>
            <CodeBlock text={prettyJson(r.paramsJson)} />
          </Section>
          {r.error && (
            <Section title="Error">
              <Alert severity="error" sx={{ whiteSpace: 'pre-wrap' }}>{r.error}</Alert>
            </Section>
          )}
          {r.resultJson && (
            <Section title={script ? 'Result' : 'Response'}>
              <CodeBlock text={prettyJson(r.resultJson)} />
            </Section>
          )}
          {script && (
            <Section title="Log">
              <RunLog key={id} runId={id} live={live} />
            </Section>
          )}
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
