import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { IconButton, Tooltip } from '@mui/material';
import { Link as RouterLink } from 'react-router';

export interface Back {
  to: string;
  label: string;
}

export function BackLink({ to, label }: Back) {
  const title = `Back to ${label}`;
  return (
    <Tooltip title={title}>
      <IconButton component={RouterLink} to={to} aria-label={title} size="small" sx={{ color: 'text.secondary' }}>
        <ArrowBackIcon fontSize="small" />
      </IconButton>
    </Tooltip>
  );
}
