import { Link } from '@mui/material';
import type { Run } from '../../api';
import type { Column } from '../layout';
import { StatusChip } from '../StatusChip';
import { formatDuration, formatTime, shortId } from '../../lib/format';

/** Columns shared by every list of runs. */
export const runColumns: Column<Run>[] = [
  { header: 'Run', render: (r) => <Link component="span">{shortId(r.id)}</Link>, mono: true, width: 100 },
  { header: 'Task', render: (r) => r.taskName ?? <em>ad-hoc script</em> },
  { header: 'Client', render: (r) => r.clientName },
  { header: 'Status', render: (r) => <StatusChip status={r.status} />, width: 150 },
  { header: 'Submitted by', render: (r) => r.submittedBy },
  { header: 'Created', render: (r) => formatTime(r.createdAt) },
  { header: 'Duration', render: (r) => formatDuration(r.startedAt, r.finishedAt) },
];
