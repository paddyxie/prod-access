import { Box, Paper, Stack, Typography } from '@mui/material';
import type { ReactNode } from 'react';

interface Props {
  title: string;
  actions?: ReactNode;
  children: ReactNode;
}

/** A titled block of a detail page. */
export function Section({ title, actions, children }: Props) {
  return (
    <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
      <Stack direction="row" sx={{ alignItems: 'center', minHeight: 40, px: 2, gap: 1, borderBottom: 1, borderColor: 'divider' }}>
        <Typography variant="subtitle1" component="h2" sx={{ fontWeight: 600, flex: 1 }}>
          {title}
        </Typography>
        {actions}
      </Stack>
      <Box sx={{ p: { xs: 1.5, sm: 2 } }}>{children}</Box>
    </Paper>
  );
}
