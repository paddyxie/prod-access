import { Box } from '@mui/material';
import type { ReactNode } from 'react';

export function PageBody({ children, narrow = false }: { children: ReactNode; narrow?: boolean }) {
  return (
    <Box sx={{ width: '100%', maxWidth: narrow ? 920 : 1552, mx: 'auto', p: { xs: 1.5, sm: 2.5 } }}>
      {children}
    </Box>
  );
}
