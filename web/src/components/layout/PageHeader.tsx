import { Box, Stack, Typography } from '@mui/material';
import type { ReactNode } from 'react';
import { consoleHeaderHeight } from '../../theme';
import { BackLink, type Back } from './BackLink';

interface Props {
  title: ReactNode;
  subtitle?: ReactNode;
  status?: ReactNode;
  actions?: ReactNode;
  back?: Back;
}

export function PageHeader({ title, subtitle, status, actions, back }: Props) {
  return (
    <Box component="header" sx={{ position: 'sticky', top: 0, zIndex: 2, bgcolor: 'background.paper', borderBottom: 1, borderColor: 'divider' }}>
      <Stack direction="row" sx={{ minHeight: consoleHeaderHeight - 1, px: { xs: 1.5, sm: 2.5 }, py: 0.75, gap: 1.25, flexWrap: 'wrap', alignItems: 'center' }}>
        {back && <BackLink {...back} />}
        <Stack direction="row" sx={{ minWidth: 0, flex: '1 1 180px', gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>
          <Typography variant="h5" component="h1" sx={{ overflowWrap: 'anywhere' }}>{title}</Typography>
          {status}
        </Stack>
        {actions && <Stack direction="row" sx={{ ml: 'auto', flexWrap: 'wrap', gap: 0.75 }}>{actions}</Stack>}
      </Stack>
      {subtitle && (
        <Typography component="div" variant="body2" color="text.secondary" sx={{ px: { xs: 1.5, sm: 2.5 }, mt: -0.25, pb: 1, overflowWrap: 'anywhere' }}>
          {subtitle}
        </Typography>
      )}
    </Box>
  );
}
