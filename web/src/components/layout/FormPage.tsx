import { Alert, Box, Button, CircularProgress, Paper, Stack } from '@mui/material';
import { useState, type FormEvent, type ReactNode } from 'react';
import { Link as RouterLink } from 'react-router';
import { BackLink, type Back } from './BackLink';
import { PageHeader } from './PageHeader';

interface Props {
  back: Back;
  title: ReactNode;
  subtitle?: ReactNode;
  submitLabel: string;
  /** Throw to keep the user on the form; the error message is shown above the fields. */
  onSubmit: () => Promise<void>;
  /** Initial data has not loaded yet. */
  loading?: boolean;
  /** Extra buttons on the left of the button row, e.g. Delete. */
  secondaryActions?: ReactNode;
  children: ReactNode;
}

/** The one layout for every create/edit/submit screen: fields in a card, Cancel + submit at the bottom. */
export function FormPage({ back, title, subtitle, submitLabel, onSubmit, loading, secondaryActions, children }: Props) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
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
    <Stack spacing={2}>
      <BackLink {...back} />
      <PageHeader title={title} subtitle={subtitle} />
      {error && <Alert severity="error">{error.message}</Alert>}
      {loading ? (
        <Box sx={{ py: 6, textAlign: 'center' }}>
          <CircularProgress />
        </Box>
      ) : (
        <Paper component="form" variant="outlined" onSubmit={submit} sx={{ p: 3 }}>
          <Stack spacing={2.5}>{children}</Stack>
          <Stack direction="row" spacing={1} sx={{ mt: 3 }}>
            {secondaryActions}
            <Box sx={{ flex: 1 }} />
            <Button component={RouterLink} to={back.to}>
              Cancel
            </Button>
            <Button type="submit" variant="contained" disabled={submitting}>
              {submitLabel}
            </Button>
          </Stack>
        </Paper>
      )}
    </Stack>
  );
}
