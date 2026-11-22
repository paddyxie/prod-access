import { Alert, Box, CircularProgress, Stack } from '@mui/material';
import type { ReactNode } from 'react';
import { type Back } from './BackLink';
import { PageHeader } from './PageHeader';
import { PageBody } from './PageBody';

interface Props {
  back: Back;
  title: ReactNode;
  subtitle?: ReactNode;
  status?: ReactNode;
  /** Actions on the shown entity (edit, run, cancel ...), top right. */
  actions?: ReactNode;
  /** The entity has not loaded yet: show a spinner instead of the sections. */
  loading?: boolean;
  error?: Error | null;
  /** <Section>s, top to bottom. */
  children?: ReactNode;
}

/** The one layout for every screen that shows a single entity. */
export function DetailPage({ back, title, subtitle, status, actions, loading, error, children }: Props) {
  return (
    <>
      <PageHeader back={back} title={title} subtitle={subtitle} status={status} actions={actions} />
      <PageBody>
        <Stack spacing={1.5}>
          {error && <Alert severity="error">{error.message}</Alert>}
          {loading ? (
            <Box sx={{ py: 6, textAlign: 'center' }}><CircularProgress aria-label="Loading details" size={28} /></Box>
          ) : children}
        </Stack>
      </PageBody>
    </>
  );
}
