import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { Link } from '@mui/material';
import { Link as RouterLink } from 'react-router';

export interface Back {
  to: string;
  label: string;
}

export function BackLink({ to, label }: Back) {
  return (
    <Link component={RouterLink} to={to} underline="hover" variant="body2" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
      <ArrowBackIcon fontSize="inherit" /> {label}
    </Link>
  );
}
