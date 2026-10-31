import { Box, Stack, Typography } from '@mui/material';
import type { ReactNode } from 'react';

interface Props {
  title: ReactNode;
  subtitle?: ReactNode;
  status?: ReactNode;
  /** Page-level buttons, always top right. */
  actions?: ReactNode;
}

export function PageHeader({ title, subtitle, status, actions }: Props) {
  return (
    <Stack direction="row" spacing={2} sx={{ alignItems: 'flex-start', flexWrap: 'wrap', rowGap: 1 }}>
      <Box sx={{ flex: 1, minWidth: 240 }}>
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
          <Typography variant="h5" component="h1" sx={{ fontWeight: 600 }}>
            {title}
          </Typography>
          {status}
        </Stack>
        {subtitle && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            {subtitle}
          </Typography>
        )}
      </Box>
      {actions && <Stack direction="row" spacing={1}>{actions}</Stack>}
    </Stack>
  );
}
