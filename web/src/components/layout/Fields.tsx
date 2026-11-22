import { Box, Typography } from '@mui/material';
import type { ReactNode } from 'react';

/** Label/value pairs of a detail section, in a responsive grid. */
export function Fields({ items }: { items: [label: string, value: ReactNode][] }) {
  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(min(180px, 100%), 1fr))', gap: 2 }}>
      {items.map(([label, value]) => (
        <Box key={label}>
          <Typography variant="caption" color="text.secondary">
            {label}
          </Typography>
          <Box sx={{ typography: 'body2', wordBreak: 'break-word' }}>{value ?? '—'}</Box>
        </Box>
      ))}
    </Box>
  );
}
