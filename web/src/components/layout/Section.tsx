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
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" sx={{ alignItems: 'center', mb: 1.5, minHeight: 32 }}>
        <Typography variant="subtitle1" component="h2" sx={{ fontWeight: 600, flex: 1 }}>
          {title}
        </Typography>
        {actions}
      </Stack>
      <Box>{children}</Box>
    </Paper>
  );
}
