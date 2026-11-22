import { Alert, Box, Button, CircularProgress, Paper, Stack } from '@mui/material';
import { useState, type FormEvent, type ReactNode } from 'react';
import { Link as RouterLink } from 'react-router';
import { type Back } from './BackLink';
import { PageHeader } from './PageHeader';
import { PageBody } from './PageBody';

interface Props {
  back: Back;
  title: ReactNode;
  subtitle?: ReactNode;
  submitLabel: string;
  /** Throw to keep the user on the form; the error message is shown above the fields. */
  onSubmit: () => Promise<void>;
  /** Initial data has not loaded yet. */
  loading?: boolean;
  loadError?: Error;
  submitDisabled?: boolean;
  /** Extra buttons on the left of the button row, e.g. Delete. */
  secondaryActions?: ReactNode;
  children: ReactNode;
}

/** The one layout for every create/edit/submit screen: fields in a card, Cancel + submit at the bottom. */
export function FormPage({ back, title, subtitle, submitLabel, onSubmit, loading, loadError, submitDisabled, secondaryActions, children }: Props) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (submitting || submitDisabled) return;
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit();
    } catch (err) {
      setError(err as Error);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <PageHeader back={back} title={title} subtitle={subtitle} />
      <PageBody narrow>
        <Stack spacing={1.5}>
          {(loadError || error) && <Alert severity="error">{(loadError ?? error)?.message}</Alert>}
          {loading && !loadError && (
            <Box sx={{ py: 6, textAlign: 'center' }}><CircularProgress aria-label="Loading form" size={28} /></Box>
          )}
          {!loading && !loadError && (
            <Paper component="form" variant="outlined" onSubmit={submit}>
              <Stack spacing={2} sx={{ p: { xs: 1.5, sm: 2.5 } }}>{children}</Stack>
              <Stack direction="row" spacing={1} sx={{ p: { xs: 1.5, sm: 2 }, borderTop: 1, borderColor: 'divider', flexWrap: 'wrap' }}>
                {secondaryActions}
                <Box sx={{ flex: 1 }} />
                <Button component={RouterLink} to={back.to}>Cancel</Button>
                <Button type="submit" variant="contained" loading={submitting} disabled={submitDisabled}>
                  {submitLabel}
                </Button>
              </Stack>
            </Paper>
          )}
        </Stack>
      </PageBody>
    </>
  );
}
