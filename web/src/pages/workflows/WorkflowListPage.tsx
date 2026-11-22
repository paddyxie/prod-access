import AddIcon from '@mui/icons-material/Add';
import { Button } from '@mui/material';
import { Link as RouterLink } from 'react-router';
import { api } from '../../api';
import { ListPage } from '../../components/layout';
import { RoleChip } from '../../components/StatusChip';
import { useCurrentUser } from '../../hooks/useCurrentUser';
import { formatTime } from '../../lib/format';
import { roleOptions } from '../../lib/options';

export function WorkflowListPage() {
  const me = useCurrentUser();
  return (
    <ListPage
      title="Workflows"
      subtitle="Reusable scripts for batch and workflow fixes. Every save is a new, immutable version. Your teams must own every role a workflow calls to run it."
      actions={
        <Button variant="contained" startIcon={<AddIcon />} component={RouterLink} to="/workflows/new">
          New workflow
        </Button>
      }
      filters={[
        { name: 'q', label: 'Name' },
        { name: 'role', label: 'Role', options: roleOptions },
      ]}
      load={(f) => api.workflows.list(f)}
      columns={[
        { header: 'Name', render: (t) => t.name },
        { header: 'Roles', render: (t) => t.roles.map((r) => <RoleChip key={r} role={r} owned={!!me?.ownedRoles.includes(r)} />) },
        { header: 'Version', render: (t) => `v${t.latestVersion}`, width: 90 },
        { header: 'Updated by', render: (t) => t.updatedBy },
        { header: 'Updated', render: (t) => formatTime(t.updatedAt) },
      ]}
      rowKey={(t) => t.id}
      rowLink={(t) => `/workflows/${t.id}`}
      emptyText="No workflows match."
    />
  );
}
