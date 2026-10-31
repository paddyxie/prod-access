import AddIcon from '@mui/icons-material/Add';
import { Button } from '@mui/material';
import { Link as RouterLink } from 'react-router';
import { api } from '../../api';
import { ListPage } from '../../components/layout';
import { AccessChip } from '../../components/StatusChip';
import { formatTime } from '../../lib/format';
import { clientOptions } from '../../lib/options';

export function TaskListPage() {
  return (
    <ListPage
      title="Tasks"
      subtitle="Reusable scripts. Every save is a new, immutable version."
      actions={
        <Button variant="contained" startIcon={<AddIcon />} component={RouterLink} to="/tasks/new">
          New task
        </Button>
      }
      filters={[
        { name: 'q', label: 'Name' },
        { name: 'clientId', label: 'Client', options: clientOptions },
      ]}
      load={(f) => api.tasks.list(f)}
      columns={[
        { header: 'Name', render: (t) => t.name },
        { header: 'Client', render: (t) => t.clientName },
        { header: 'Access', render: (t) => <AccessChip access={t.hasWrite ? 'WRITE' : 'READ'} />, width: 100 },
        { header: 'Version', render: (t) => `v${t.latestVersion}`, width: 90 },
        { header: 'Updated by', render: (t) => t.updatedBy },
        { header: 'Updated', render: (t) => formatTime(t.updatedAt) },
      ]}
      rowKey={(t) => t.id}
      rowLink={(t) => `/tasks/${t.id}`}
      emptyText="No tasks match."
    />
  );
}
