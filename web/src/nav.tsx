import BuildIcon from '@mui/icons-material/Build';
import DescriptionIcon from '@mui/icons-material/Description';
import PlayCircleIcon from '@mui/icons-material/PlayCircle';
import type { ReactNode } from 'react';

/** Top-level sections, in menu order. Each is a list page at `path`; its sub-pages live under it. */
export const NAV: { path: string; label: string; icon: ReactNode }[] = [
  { path: '/runs', label: 'Runs', icon: <PlayCircleIcon /> },
  { path: '/tasks', label: 'Tasks', icon: <DescriptionIcon /> },
  { path: '/tools', label: 'Tools', icon: <BuildIcon /> },
];
