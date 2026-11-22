import type { Run } from '../../api';
import type { Column } from '../layout';
import { StatusChip } from '../StatusChip';
import { formatDuration, formatTime, shortId } from '../../lib/format';

/** What a run ran: its workflow, an ad-hoc script, or the one tool it called. */
export function runSubject(r: Run) {
  if (r.kind === 'CALL') return <code>{r.toolKey}</code>;
  return r.workflowName ?? <em>ad-hoc script</em>;
}

/** Columns shared by every list of runs. */
export const runColumns: Column<Run>[] = [
  { header: 'Run', render: (r) => shortId(r.id), mono: true, width: 100 },
  { header: 'What', render: runSubject },
  { header: 'Roles', render: (r) => (JSON.parse(r.rolesJson) as string[]).join(', ') },
  { header: 'Status', render: (r) => <StatusChip status={r.status} />, width: 150 },
  { header: 'Submitted by', render: (r) => r.submittedBy },
  { header: 'Created', render: (r) => formatTime(r.createdAt) },
  { header: 'Duration', render: (r) => formatDuration(r.startedAt, r.finishedAt) },
];
