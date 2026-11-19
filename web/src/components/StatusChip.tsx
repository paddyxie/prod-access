import { Chip, type ChipProps } from '@mui/material';
import type { RunStatus, ToolType } from '../api';

const STATUS_COLOR: Record<RunStatus, ChipProps['color']> = {
  QUEUED: 'default',
  RUNNING: 'info',
  SUCCEEDED: 'success',
  FAILED: 'error',
  CANCELLED: 'default',
  TIMED_OUT: 'error',
  LOST: 'error',
};

export function StatusChip({ status }: { status: RunStatus }) {
  return <Chip size="small" color={STATUS_COLOR[status]} label={status.replace('_', ' ')} />;
}

export const TOOL_TYPE_LABEL: Record<ToolType, string> = { API_FORGE: 'API Forge', HTTP_CLIENT: 'HTTP Client' };

export function ToolTypeChip({ type }: { type: ToolType }) {
  return <Chip size="small" variant="outlined" color={type === 'API_FORGE' ? 'primary' : 'secondary'} label={TOOL_TYPE_LABEL[type]} />;
}

/** A provider role, marked when none of the current user's teams owns it (its tools cannot be called). */
export function RoleChip({ role, owned }: { role: string; owned: boolean }) {
  return <Chip size="small" variant={owned ? 'filled' : 'outlined'} color={owned ? 'default' : 'warning'} label={owned ? role : `${role} · not your team`} />;
}
