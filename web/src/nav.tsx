import ApiIcon from '@mui/icons-material/Api';
import BadgeIcon from '@mui/icons-material/Badge';
import DescriptionIcon from '@mui/icons-material/Description';
import FactCheckIcon from '@mui/icons-material/FactCheck';
import PlayCircleIcon from '@mui/icons-material/PlayCircle';
import type { ReactNode } from 'react';

/** Top-level sections, in menu order. Each is a list page at `path`; its sub-pages live under it. */
export const NAV: { path: string; label: string; icon: ReactNode }[] = [
  { path: '/runs', label: 'Runs', icon: <PlayCircleIcon /> },
  { path: '/approvals', label: 'Approvals', icon: <FactCheckIcon /> },
  { path: '/tasks', label: 'Tasks', icon: <DescriptionIcon /> },
  { path: '/clients', label: 'Clients', icon: <BadgeIcon /> },
  { path: '/apis', label: 'API list', icon: <ApiIcon /> },
];
