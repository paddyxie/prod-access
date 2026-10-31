import AddIcon from '@mui/icons-material/Add';
import { Button, Chip } from '@mui/material';
import { Link as RouterLink } from 'react-router';
import { api } from '../../api';
import { ListPage } from '../../components/layout';
import { formatTime } from '../../lib/format';

export function ClientListPage() {
  return (
    <ListPage
      title="Clients"
      subtitle="App identities that runs call the API Gateway as. Secrets stay in the credential store; only the reference is kept here."
      actions={
        <Button variant="contained" startIcon={<AddIcon />} component={RouterLink} to="/clients/new">
          New client
        </Button>
      }
      filters={[{ name: 'q', label: 'Name, description or ref' }]}
      load={(f) => api.clients.list(f)}
      columns={[
        { header: 'Name', render: (c) => c.name },
        { header: 'Environment', render: (c) => c.environment },
        { header: 'Credential ref', render: (c) => c.credentialRef, mono: true },
        { header: 'State', render: (c) => <Chip size="small" color={c.enabled ? 'success' : 'default'} label={c.enabled ? 'Enabled' : 'Disabled'} /> },
        { header: 'Description', render: (c) => c.description },
        { header: 'Created', render: (c) => formatTime(c.createdAt) },
      ]}
      rowKey={(c) => c.id}
      rowLink={(c) => `/clients/${c.id}/edit`}
      emptyText="No clients match."
    />
  );
}
