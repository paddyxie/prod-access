import { Chip, type ChipProps } from '@mui/material';
import type { Access, RunStatus } from '../api';

const STATUS_COLOR: Record<RunStatus, ChipProps['color']> = {
  PENDING_APPROVAL: 'warning',
  QUEUED: 'default',
  RUNNING: 'info',
  SUCCEEDED: 'success',
  FAILED: 'error',
  CANCELLED: 'default',
  TIMED_OUT: 'error',
  REJECTED: 'error',
  LOST: 'error',
};

export function StatusChip({ status }: { status: RunStatus }) {
  return <Chip size="small" color={STATUS_COLOR[status]} label={status.replace('_', ' ')} />;
}

export function AccessChip({ access }: { access: Access }) {
  return <Chip size="small" variant="outlined" color={access === 'WRITE' ? 'warning' : 'default'} label={access} />;
}
