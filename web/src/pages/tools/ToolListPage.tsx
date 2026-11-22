import AddIcon from '@mui/icons-material/Add';
import { Button } from '@mui/material';
import { Link as RouterLink } from 'react-router';
import { api } from '../../api';
import { ListPage } from '../../components/layout';
import { RoleChip, ToolTypeChip } from '../../components/StatusChip';
import { useCurrentUser } from '../../hooks/useCurrentUser';
import { roleOptions, toolTypeOptions } from '../../lib/options';

export function ToolListPage() {
  const me = useCurrentUser();
  return (
    <ListPage
      title="Tools"
      subtitle="API contracts registered under each system."
      actions={
        <Button variant="contained" startIcon={<AddIcon />} component={RouterLink} to="/tools/new">
          New tool
        </Button>
      }
      filters={[
        { name: 'q', label: 'Name, path or description' },
        { name: 'role', label: 'Role', options: roleOptions },
        { name: 'type', label: 'Type', options: toolTypeOptions },
      ]}
      load={(f) => api.tools.list(f)}
      columns={[
        { header: 'Tool', render: (t) => `${t.role}.${t.name}`, mono: true },
        { header: 'Version', render: (t) => `v${t.ver}` },
        { header: 'Service', render: (t) => t.service },
        { header: 'Type', render: (t) => <ToolTypeChip type={t.type} />, width: 130 },
        { header: 'Request', render: (t) => `${t.method} ${t.baseUrl ?? ''}${t.pathTemplate}`, mono: true },
        { header: 'Description', render: (t) => t.description },
        { header: 'Role', render: (t) => <RoleChip role={t.role} owned={!!me?.ownedRoles.includes(t.role)} /> },
      ]}
      rowKey={(t) => t.id}
      rowLink={(t) => `/tools/${t.id}`}
      emptyText="No tools match."
    />
  );
}
