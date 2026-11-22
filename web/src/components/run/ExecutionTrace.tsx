import ExpandMore from '@mui/icons-material/ExpandMore';
import { Accordion, AccordionDetails, AccordionSummary, Alert, Box, Chip, Link, Stack, Typography } from '@mui/material';
import { Link as RouterLink } from 'react-router';
import type { ApiCallRecord, ExecutionAction, ToolCallRecord } from '../../api';
import { formatTime, prettyJson } from '../../lib/format';
import { monoFont } from '../../theme';
import { Fields } from '../layout';
import { CodeBlock } from '../layout/CodeBlock';

function Payload({ label, value }: { label: string; value: string | null }) {
  if (value === null) return null;
  return (
    <Stack spacing={0.5} sx={{ minWidth: 0 }}>
      <Typography color="text.secondary" variant="caption">{label}</Typography>
      <CodeBlock text={prettyJson(value)} />
    </Stack>
  );
}

function TraceState({ status }: { status: string }) {
  const color = status === 'SUCCEEDED' ? 'success' : status === 'RUNNING' ? 'info' : status === 'UNKNOWN' ? 'warning' : 'error';
  return <Chip label={status.replaceAll('_', ' ')} size="small" variant="outlined" color={color} />;
}

function HttpAttempt({ request: r }: { request: ApiCallRecord }) {
  return (
    <Stack spacing={1.25} sx={{ border: 1, borderColor: 'divider', borderRadius: 1, p: { xs: 1, sm: 1.5 } }}>
      <Stack direction="row" sx={{ gap: 0.75, flexWrap: 'wrap', alignItems: 'center' }}>
        <Typography variant="subtitle2">Attempt {r.attempt}</Typography>
        <Chip label={r.method} variant="outlined" size="small" />
        <Typography variant="body2" sx={{ minWidth: 0, overflowWrap: 'anywhere', fontFamily: monoFont }}>{r.url}</Typography>
      </Stack>
      <Fields items={[
        ['State', <TraceState status={r.status} />], ['HTTP status', r.responseStatus],
        ['Started', formatTime(r.startedAt)], ['Finished', formatTime(r.finishedAt)],
      ]} />
      {(r.requestTruncated || r.responseTruncated) && <Alert severity="warning">Captured HTTP payload is truncated.</Alert>}
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: 'repeat(2, minmax(0, 1fr))' }, gap: 1.5 }}>
        <Stack spacing={0.75}>
          <Payload label="Request headers" value={r.requestHeadersJson} />
          <Payload label="Request body" value={r.requestBody} />
        </Stack>
        <Stack spacing={0.75}>
          <Payload label="Response headers" value={r.responseHeadersJson} />
          <Payload label="Response body" value={r.responseBody} />
        </Stack>
      </Box>
      {r.error && <Alert severity="error">{r.error}</Alert>}
    </Stack>
  );
}

export function ExecutionTrace({ calls, requests, actions, selectedInvocation }: {
  calls: ToolCallRecord[];
  requests: ApiCallRecord[];
  actions: ExecutionAction[];
  selectedInvocation: string | null;
}) {
  const actionsById = new Map(actions.map((action) => [action.id, action]));
  const requestsByInvocation = new Map<string, ApiCallRecord[]>();
  for (const request of requests) {
    const attempts = requestsByInvocation.get(request.invocationId);
    if (attempts) attempts.push(request);
    else requestsByInvocation.set(request.invocationId, [request]);
  }

  return (
    <Stack spacing={1.5}>
      {actions.length > 0 && <Box>
        <Typography variant="subtitle2" component="h3" sx={{ mb: 0.75 }}>Actions</Typography>
        {actions.map((action) => (
          <Accordion key={action.id} variant="outlined">
            <AccordionSummary expandIcon={<ExpandMore fontSize="small" />}>
              <Stack direction="row" sx={{ alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                <Typography variant="body2" sx={{ fontWeight: 650 }}>{action.name}</Typography>
                <TraceState status={action.status} />
                <Typography color="text.secondary" variant="caption">{formatTime(action.startedAt)}</Typography>
              </Stack>
            </AccordionSummary>
            <AccordionDetails>
              <Stack spacing={1.5}>
                <Fields items={[
                  ['Action ID', action.id], ['Parent action', action.parentActionId && (actionsById.get(action.parentActionId)?.name ?? action.parentActionId)],
                  ['Finished', formatTime(action.finishedAt)],
                ]} />
                <Payload label="Action input" value={action.inputJson} />
                <Payload label="Action output" value={action.resultJson} />
                {action.error && <Alert severity="error">{action.error}</Alert>}
              </Stack>
            </AccordionDetails>
          </Accordion>
        ))}
      </Box>}
      <Box>
        <Typography variant="subtitle2" component="h3" sx={{ mb: 0.75 }}>Tool invocations</Typography>
        {calls.length === 0 && <Typography color="text.secondary" variant="body2">No tool invocations.</Typography>}
        {calls.map((call) => (
          <Accordion key={`${call.id}:${selectedInvocation === call.id}`} defaultExpanded={selectedInvocation === call.id} variant="outlined">
            <AccordionSummary expandIcon={<ExpandMore fontSize="small" />}>
              <Stack direction="row" sx={{ alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                <Typography variant="body2" sx={{ fontWeight: 650, overflowWrap: 'anywhere' }}>{call.service} / {call.role}.{call.tool} v{call.ver}</Typography>
                <TraceState status={call.status} />
                <Typography color="text.secondary" variant="caption">{call.actor}</Typography>
              </Stack>
            </AccordionSummary>
            <AccordionDetails>
              <Stack spacing={1.5}>
                <Fields items={[
                  ['Invocation ID', call.id], ['Action', call.actionId && (actionsById.get(call.actionId)?.name ?? call.actionId)],
                  ['Started', formatTime(call.startedAt)], ['Finished', formatTime(call.finishedAt)],
                ]} />
                {(call.inputTruncated || call.outputTruncated) && <Alert severity="warning">Captured invocation payload is truncated.</Alert>}
                <Payload label="Tool input" value={call.inputJson} />
                <Payload label="Tool output" value={call.outputJson} />
                {call.error && <Alert severity="error">{call.error}</Alert>}
                {requestsByInvocation.get(call.id)?.map((request) => <HttpAttempt key={request.id} request={request} />)}
              </Stack>
            </AccordionDetails>
          </Accordion>
        ))}
        {selectedInvocation && calls.every((call) => call.id !== selectedInvocation) && calls.length > 0 && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>Invocation unavailable. <Link component={RouterLink} to=".">Show this execution</Link></Typography>
        )}
      </Box>
    </Stack>
  );
}
